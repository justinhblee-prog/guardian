import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { pathToFileURL } from 'node:url';
import { publishManifest } from './publish-manifest.mjs';

const MAX_FILTER_BYTES = 10 * 1024 * 1024;

async function readResponse(response) {
  if (!response.ok) throw new Error(`Upstream returned HTTP ${response.status}.`);
  const length = Number(response.headers.get('content-length') || 0);
  if (length > MAX_FILTER_BYTES) throw new Error('Upstream filter exceeds 10 MiB.');
  const chunks = [];
  let size = 0;
  for await (const chunk of response.body) {
    size += chunk.length;
    if (size > MAX_FILTER_BYTES) throw new Error('Upstream filter exceeds 10 MiB.');
    chunks.push(Buffer.from(chunk));
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
  const results = [];
  for (const source of config.sources) {
    if (!source || typeof source.id !== 'string' || !/^[a-zA-Z0-9_-]{1,64}$/.test(source.id) || typeof source.url !== 'string' ||
        typeof source.minimumRules !== 'number' || !Number.isSafeInteger(source.minimumRules) ||
        source.minimumRules < 1) throw new Error('Each source requires id, HTTPS url and positive minimumRules.');
    const upstream = new URL(source.url);
    if (upstream.protocol !== 'https:' || upstream.username || upstream.password ||
        upstream.search || upstream.hash) throw new Error(`Source ${source.id} must use a clean HTTPS URL.`);
    const response = await fetchImpl(upstream, {
      method: 'GET', redirect: 'error',
      headers: { accept: 'text/plain,*/*;q=0.8', 'user-agent': 'GuardianFilterUpdater/1.0' },
      signal: AbortSignal.timeout(30_000)
    });
    const { bytes, text } = await readResponse(response);
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
    const inputPath = path.resolve(config.tempDir || '.guardian-filter-update', `${source.id}-${sourceVersion}.txt`);
    const { writeFile, mkdir } = await import('node:fs/promises');
    await mkdir(path.dirname(inputPath), { recursive: true });
    await writeFile(inputPath, bytes, { flag: 'wx', mode: 0o600 });
    try {
      const published = await publishManifest({
        'source-id': source.id,
        'source-version': sourceVersion,
        input: inputPath,
        'base-url': config.baseUrl,
        'minimum-rules': String(source.minimumRules),
        'output-dir': config.outputDir || 'dist'
      }, env);
      results.push({ sourceId: source.id, sourceVersion, ruleCount, sha256: digest, manifestPath: published.manifestPath });
    } finally {
      const { rm } = await import('node:fs/promises');
      await rm(inputPath, { force: true });
    }
  }
  return results;
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
