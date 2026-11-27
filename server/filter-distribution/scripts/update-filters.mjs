import { createHash, createPrivateKey, randomUUID } from 'node:crypto';
import { lstat, open, readFile, mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { pathToFileURL } from 'node:url';
import { publishManifest } from './publish-manifest.mjs';

const MAX_FILTER_BYTES = 10 * 1024 * 1024;
const MAX_TOTAL_FILTER_BYTES = 64 * 1024 * 1024;
const MAX_SOURCES = 100;

// Only remove the batch lock if the pathname still refers to the inode this
// process created. This avoids deleting a replacement lock after an unlink /
// recreate race in a shared output directory.
async function removeOwnedBatchLock(lockHandle, lockPath) {
  const owned = await lockHandle.stat({ bigint: true });
  try {
    const current = await lstat(lockPath, { bigint: true });
    if (current.isFile() && current.dev === owned.dev && current.ino === owned.ino) {
      await rm(lockPath);
    }
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
}

async function readResponse(response) {
  if (!response.ok) throw new Error(`Upstream returned HTTP ${response.status}.`);
  const declaredLength = response.headers.get('content-length');
  let expectedLength = null;
  if (declaredLength !== null) {
    if (!/^\d+$/.test(declaredLength)) {
      throw new Error('Upstream returned an invalid Content-Length.');
    }
    const length = Number(declaredLength);
    if (!Number.isSafeInteger(length) || length <= 0 || length > MAX_FILTER_BYTES) {
      throw new Error('Upstream filter has an invalid or oversized Content-Length.');
    }
    expectedLength = length;
  }
  if (!response.body) throw new Error('Upstream response has no readable body.');
  const chunks = [];
  let size = 0;
  for await (const chunk of response.body) {
    size += chunk.length;
    if (size > MAX_FILTER_BYTES) throw new Error('Upstream filter exceeds 10 MiB.');
    chunks.push(Buffer.from(chunk));
  }
  if (expectedLength !== null && size !== expectedLength) {
    throw new Error(`Upstream Content-Length mismatch: declared ${expectedLength} bytes, received ${size}.`);
  }
  const bytes = Buffer.concat(chunks);
  const text = bytes.toString('utf8');
  if (!bytes.length || !Buffer.from(text, 'utf8').equals(bytes) || bytes.includes(0)) {
    throw new Error('Upstream filter must be non-empty valid UTF-8 without NUL bytes.');
  }
  return { bytes, text };
}

export async function updateFilters({ config, env = process.env, fetchImpl = fetch, now = new Date() }) {
  const base = new URL(config.baseUrl);
  if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash) {
    throw new Error('baseUrl must be a clean HTTPS URL.');
  }
  if (!Array.isArray(config.sources) || config.sources.length === 0) {
    throw new Error('Config must contain a non-empty sources array.');
  }
  if (config.sources.length > MAX_SOURCES) {
    throw new Error(`Config may contain at most ${MAX_SOURCES} filter sources.`);
  }
  // Fail before downloading large upstream files if signing is unavailable.
  const signingKeyText = env.GUARDIAN_FILTER_SIGNING_PRIVATE_KEY;
  if (typeof signingKeyText !== 'string' || !signingKeyText.trim()) {
    throw new Error('GUARDIAN_FILTER_SIGNING_PRIVATE_KEY is required.');
  }
  const signingKey = createPrivateKey(signingKeyText.replace(/\\n/g, '\n'));
  const signingKeyBits = signingKey.asymmetricKeyDetails?.modulusLength || 0;
  if (signingKey.asymmetricKeyType !== 'rsa' || signingKeyBits < 2048 || signingKeyBits > 4096) {
    throw new Error('Signing key must be RSA with a modulus between 2048 and 4096 bits.');
  }

  // Validate the entire source list before any network request or publication.
  // Duplicate IDs would otherwise publish the same mutable manifest twice in
  // one run, making the final source silently depend on array order.
  const seenSourceIds = new Set();
  const normalizedSources = config.sources.map(source => {
    if (!source || typeof source.id !== 'string' ||
        !/^[a-zA-Z0-9_-]{1,64}$/.test(source.id) ||
        typeof source.url !== 'string' ||
        typeof source.minimumRules !== 'number' ||
        !Number.isSafeInteger(source.minimumRules) || source.minimumRules < 1) {
      throw new Error('Each source requires id, HTTPS url and positive minimumRules.');
    }
    if (seenSourceIds.has(source.id)) {
      throw new Error(`Duplicate source id in config: ${source.id}.`);
    }
    seenSourceIds.add(source.id);
    const upstream = new URL(source.url);
    if (upstream.protocol !== 'https:' || upstream.username || upstream.password ||
        upstream.search || upstream.hash) {
      throw new Error(`Source ${source.id} must use a clean HTTPS URL.`);
    }
    // A license label alone does not establish the provenance of every
    // upstream component in an aggregated list. Require a traceable, source-
    // specific compliance review before any upstream request or publication.
    const cleanHttpsReference = value => {
      if (typeof value !== 'string' || !value.trim()) return false;
      try {
        const parsed = new URL(value);
        return parsed.protocol === 'https:' && !parsed.username && !parsed.password &&
          !parsed.search && !parsed.hash;
      } catch {
        return false;
      }
    };
    const pinnedVersion = typeof source.upstreamVersion === 'string' &&
      /^[A-Za-z0-9._+-]{1,128}$/.test(source.upstreamVersion) &&
      !/^(rolling(?:-unpinned)?|unpinned|latest|main|master|head)$/i.test(source.upstreamVersion);
    const commitSha = typeof source.upstreamCommitSha === 'string' &&
      /^[a-f0-9]{40,64}$/i.test(source.upstreamCommitSha);
    const sourceUrlPinsCommit = commitSha && (() => {
      const segments = upstream.pathname.split('/').filter(Boolean);
      return segments.some(segment =>
        segment.toLowerCase() === source.upstreamCommitSha.toLowerCase() ||
        segment.toLowerCase().endsWith('@' + source.upstreamCommitSha.toLowerCase())
      );
    })();
    const pinnedSourceUrl = sourceUrlPinsCommit &&
      !/@(?:latest|main|master|head)(?:\/|$)/i.test(source.url);
    if (!cleanHttpsReference(source.upstreamRepository) ||
        !cleanHttpsReference(source.licenseUrl) ||
        !pinnedVersion ||
        !pinnedSourceUrl ||
        source.thirdPartyProvenanceReviewed !== true ||
        typeof source.complianceEvidenceRef !== 'string' ||
        !source.complianceEvidenceRef.trim() ||
        source.complianceEvidenceRef.length > 256) {
      throw new Error(
        `Source ${source.id} is missing its upstream repository, license URL, pinned upstream version/commit URL, third-party provenance review or compliance evidence reference.`
      );
    }
    if (typeof source.licenseId !== 'string' ||
        !/^[A-Za-z0-9.+-]{1,64}$/.test(source.licenseId) ||
        source.redistributionApproved !== true ||
        typeof source.redistributionApprovalRef !== 'string' ||
        !source.redistributionApprovalRef.trim() ||
        source.redistributionApprovalRef.length > 128) {
      throw new Error(
        `Source ${source.id} is not approved for redistribution; set licenseId, redistributionApproved=true and redistributionApprovalRef only after license-compliance review.`
      );
    }
    return { ...source, upstream };
  });

  // Serialize whole update batches. Per-source publisher locks alone cannot
  // prevent two scheduled runs from interleaving different source generations.
  const outputDir = path.resolve(config.outputDir || 'dist');
  await mkdir(outputDir, { recursive: true });
  const lockPath = path.join(outputDir, '.guardian-update.lock');
  let batchLock;
  try {
    batchLock = await open(lockPath, 'wx', 0o600);
  } catch (error) {
    if (error.code === 'EEXIST') {
      throw new Error('Another filter update batch is active, or a stale .guardian-update.lock requires manual review.');
    }
    throw error;
  }
  try {
    await batchLock.writeFile(JSON.stringify({
      pid: process.pid,
      startedAt: new Date().toISOString()
    }) + '\n');
    await batchLock.sync();
  } catch (error) {
    try {
      await removeOwnedBatchLock(batchLock, lockPath);
    } finally {
      await batchLock.close();
    }
    throw error;
  }

  // Stage and validate every upstream response before publishing any source.
  // A later upstream outage must not leave a mixed-generation distribution.
  const staged = [];
  let totalDownloadedBytes = 0;
  try {
    for (const source of normalizedSources) {
      const { upstream } = source;
      const response = await fetchImpl(upstream, {
        method: 'GET', redirect: 'error',
        headers: { accept: 'text/plain,*/*;q=0.8', 'user-agent': 'GuardianFilterUpdater/1.0' },
        signal: AbortSignal.timeout(30_000)
      });
      const { bytes, text } = await readResponse(response);
      totalDownloadedBytes += bytes.length;
      if (totalDownloadedBytes > MAX_TOTAL_FILTER_BYTES) {
        throw new Error('Combined upstream filters exceed the 64 MiB batch limit.');
      }
      const ruleCount = text.split(/\r?\n/).filter(line => {
        const value = line.trim();
        return value && !value.startsWith('!') && !value.startsWith('#') &&
          !value.startsWith('[') && !value.startsWith('@@');
      }).length;
      if (ruleCount < source.minimumRules) {
        throw new Error(`Source ${source.id} has ${ruleCount} rules; minimum is ${source.minimumRules}.`);
      }
      const digest = createHash('sha256').update(bytes).digest('hex');
      const date = now.toISOString().slice(0, 10).replaceAll('-', '');
      const sourceVersion = `${date}-${digest.slice(0, 12)}`;
      const inputPath = path.resolve(config.tempDir || '.guardian-filter-update', `${source.id}-${sourceVersion}-${randomUUID()}.txt`);
      await mkdir(path.dirname(inputPath), { recursive: true, mode: 0o700 });
      let stagedHandle;
      try {
        stagedHandle = await open(inputPath, 'wx', 0o600);
        await stagedHandle.writeFile(bytes);
        // Ensure the fully validated staging file reaches storage before the
        // publisher opens it and makes its immutable copy.
        await stagedHandle.sync();
        await stagedHandle.close();
        stagedHandle = undefined;
        staged.push({ source, inputPath, sourceVersion, ruleCount, digest });
      } catch (error) {
        // A failed write can leave a partial file before it is registered in
        // the staged list; close and remove it before propagating the failure.
        try {
          await stagedHandle?.close();
        } finally {
          await rm(inputPath, { force: true });
        }
        throw error;
      }
    }

    const results = [];
    for (const item of staged) {
      const published = await publishManifest({
        'source-id': item.source.id,
        'source-version': item.sourceVersion,
        input: item.inputPath,
        'base-url': config.baseUrl,
        'minimum-rules': String(item.source.minimumRules),
        'output-dir': config.outputDir || 'dist'
      }, env);
      results.push({
        sourceId: item.source.id,
        sourceVersion: item.sourceVersion,
        ruleCount: item.ruleCount,
        sha256: item.digest,
        manifestPath: published.manifestPath
      });
    }
    return results;
  } finally {
    try {
      await Promise.all(staged.map(item => rm(item.inputPath, { force: true })));
    } finally {
      try {
        await removeOwnedBatchLock(batchLock, lockPath);
      } finally {
        await batchLock.close();
      }
    }
  }
}

async function main() {
  const configPath = path.resolve(process.argv[2] || 'sources.json');
  const config = JSON.parse(await readFile(configPath, 'utf8'));
  const results = await updateFilters({ config });
  process.stdout.write(JSON.stringify(results, null, 2) + '\n');
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch(error => {
    process.stderr.write(`Filter update failed: ${error.message}\n`);
    process.exitCode = 1;
  });
}
