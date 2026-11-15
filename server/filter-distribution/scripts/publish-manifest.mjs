import { createPrivateKey, createPublicKey, randomUUID, sign as rsaSign } from 'node:crypto';
import { constants as fsConstants } from 'node:fs';
import { lstat, mkdir, open, readFile, rename, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { pathToFileURL } from 'node:url';

const MAX_FILTER_BYTES = 10 * 1024 * 1024;
const MAX_MANIFEST_BYTES = 16 * 1024;

function argsFrom(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    if (!key.startsWith('--')) throw new Error(`Unexpected argument: ${key}`);
    const name = key.slice(2);
    if (name === 'help') { args.help = true; continue; }
    const value = argv[index + 1];
    if (!value || value.startsWith('--')) throw new Error(`Missing value for --${name}`);
    args[name] = value;
    index += 1;
  }
  return args;
}

async function atomicWrite(filePath, bytes) {
  const directory = path.dirname(filePath);
  await mkdir(directory, { recursive: true });
  const temporary = `${filePath}.${randomUUID()}.tmp`;
  let handle;
  try {
    handle = await open(temporary, 'wx', 0o644);
    await handle.writeFile(bytes);
    // Flush file contents before the rename so a power loss cannot publish
    // a manifest whose temporary file data was never committed to storage.
    await handle.sync();
    await handle.close();
    handle = undefined;
    await rename(temporary, filePath);

    // Directory fsync makes the rename durable on POSIX filesystems. Windows
    // does not generally expose directory handles through Node, so retain the
    // atomic rename guarantee there and skip only this platform-specific step.
    if (process.platform !== 'win32') {
      let directoryHandle;
      try {
        directoryHandle = await open(directory, fsConstants.O_RDONLY);
        await directoryHandle.sync();
      } catch (error) {
        // Some POSIX-backed virtual/container filesystems do not implement
        // directory fsync. Do not hide ordinary I/O or permission failures.
        if (!['EINVAL', 'ENOTSUP', 'EOPNOTSUPP', 'ENOSYS'].includes(error.code)) throw error;
      } finally {
        await directoryHandle?.close();
      }
    }
  } finally {
    if (handle) await handle.close();
    await rm(temporary, { force: true });
  }
}

async function readExistingManifest(filePath) {
  // The anti-rollback source is security-sensitive: never follow a symlink or
  // allocate unbounded memory while reading a previously published manifest.
  const handle = await open(filePath, fsConstants.O_RDONLY | fsConstants.O_NOFOLLOW);
  try {
    const stat = await handle.stat();
    if (!stat.isFile() || stat.size < 1 || stat.size > MAX_MANIFEST_BYTES) {
      throw new Error('Existing manifest must be a non-empty regular file no larger than 16 KiB.');
    }
    const bytes = await handle.readFile();
    if (bytes.length !== stat.size || bytes.length > MAX_MANIFEST_BYTES) {
      throw new Error('Existing manifest changed or exceeded 16 KiB while being read.');
    }
    const text = bytes.toString('utf8');
    if (!Buffer.from(text, 'utf8').equals(bytes)) {
      throw new Error('Existing manifest is not valid UTF-8.');
    }
    return text;
  } finally {
    await handle.close();
  }
}

async function readFilterInput(filePath) {
  // Refuse symlinks and read through one descriptor so validation and hashing
  // apply to the same regular file, not a path swapped mid-read.
  const handle = await open(filePath, fsConstants.O_RDONLY | fsConstants.O_NOFOLLOW);
  try {
    const stat = await handle.stat();
    if (!stat.isFile() || stat.size < 1 || stat.size > MAX_FILTER_BYTES) {
      throw new Error('Filter input must be a non-empty regular file no larger than 10 MiB.');
    }
    const content = await handle.readFile();
    if (content.length !== stat.size || content.length > MAX_FILTER_BYTES) {
      throw new Error('Filter input changed or exceeded 10 MiB while being read.');
    }
    return content;
  } finally {
    await handle.close();
  }
}

async function publishManifestUnlocked(options, env = process.env) {
  const sourceId = options['source-id'];
  const sourceVersion = options['source-version'];
  const input = options.input;
  const baseUrl = options['base-url'];
  const minimumRules = Number(options['minimum-rules']);
  const outputDir = path.resolve(options['output-dir'] || 'dist');
  const privateKeyText = env.GUARDIAN_FILTER_SIGNING_PRIVATE_KEY;

  if (typeof sourceId !== 'string' || sourceId === '.' || sourceId === '..' || !/^[a-zA-Z0-9_-]{1,64}$/.test(sourceId)) {
    throw new Error('source-id must contain 1-64 letters, digits, underscore or hyphen.');
  }
  if (typeof sourceVersion !== 'string' || sourceVersion === '.' || sourceVersion === '..' || !/^[a-zA-Z0-9._+-]{1,64}$/.test(sourceVersion)) {
    throw new Error('source-version must contain 1-64 safe version characters.');
  }
  if (!input) throw new Error('--input is required.');
  if (!Number.isSafeInteger(minimumRules) || minimumRules < 1 || minimumRules > 2147483647) {
    throw new Error('--minimum-rules must be a positive integer.');
  }
  if (typeof privateKeyText !== 'string' || !privateKeyText.trim()) {
    throw new Error('GUARDIAN_FILTER_SIGNING_PRIVATE_KEY is required.');
  }

  const normalizedPrivateKey = privateKeyText.replace(/\\n/g, '\n');
  const signingKey = createPrivateKey(normalizedPrivateKey);
  const signingKeyBits = signingKey.asymmetricKeyDetails?.modulusLength || 0;
  if (signingKey.asymmetricKeyType !== 'rsa' || signingKeyBits < 2048 || signingKeyBits > 4096) {
    throw new Error('Signing key must be RSA with a modulus between 2048 and 4096 bits.');
  }

  const publicBase = new URL(baseUrl);
  if (publicBase.protocol !== 'https:' || !publicBase.hostname ||
      publicBase.username || publicBase.password || publicBase.search || publicBase.hash) {
    throw new Error('--base-url must be an HTTPS origin/path without credentials, query or fragment.');
  }

  const content = await readFilterInput(input);
  if (content.length === 0 || content.length > MAX_FILTER_BYTES || content.includes(0)) {
    throw new Error('Filter content is empty, contains NUL, or exceeds 10 MiB.');
  }
  const contentText = content.toString('utf8');
  if (!Buffer.from(contentText, 'utf8').equals(content)) {
    throw new Error('Filter content must be valid UTF-8.');
  }

  const manifestPath = path.join(outputDir, `${sourceId}.manifest`);
  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  let previousGeneratedAt = 0;
  try {
    const previous = await readExistingManifest(manifestPath);
    const previousLines = previous.trimEnd().split('\n');
    const previousSourceIds = previousLines.filter(line => line.startsWith('sourceId='));
    const generatedAtLines = previousLines.filter(line => line.startsWith('generatedAt='));
    const match = /^generatedAt=(\d+)$/.exec(generatedAtLines[0] || '');
    if (previousSourceIds.length !== 1 || previousSourceIds[0] !== `sourceId=${sourceId}` ||
        generatedAtLines.length !== 1 || !match) {
      throw new Error('Existing manifest identity or generatedAt is invalid; refusing to reset anti-rollback time.');
    }
    previousGeneratedAt = Number(match[1]);
    if (!Number.isSafeInteger(previousGeneratedAt) || previousGeneratedAt <= 0 ||
        previousGeneratedAt > nowEpochSeconds + 600) {
      throw new Error('Existing manifest generatedAt is invalid or too far in the future.');
    }
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
  const generatedAt = Math.max(nowEpochSeconds, previousGeneratedAt + 1);
  if (generatedAt > nowEpochSeconds + 600) {
    throw new Error('Cannot publish within the app accepted future timestamp window.');
  }

  const publicKey = createPublicKey(signingKey)
    .export({ type: 'spki', format: 'der' }).toString('base64');
  // Prepare optional client configuration before writing immutable filter
  // content, detached signatures or the manifest commit marker.
  if (options['public-key-out']) {
    await atomicWrite(path.resolve(options['public-key-out']), Buffer.from(publicKey + '\n', 'ascii'));
  }

  const filterRelativePath = path.join('filters', sourceId, `${sourceVersion}.txt`);
  const filterPath = path.join(outputDir, filterRelativePath);
  try {
    const existing = await readFilterInput(filterPath);
    if (!existing.equals(content)) {
      throw new Error('An immutable sourceVersion already exists with different filter content.');
    }
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
    await atomicWrite(filterPath, content);
  }

  const sha256 = (await import('node:crypto')).createHash('sha256').update(content).digest('hex');
  const filterUrl = new URL(filterRelativePath.split(path.sep).join('/'), publicBase.href.endsWith('/') ? publicBase : new URL(`${publicBase.href}/`)).href;
  const manifestText = [
    'manifestVersion=1',
    `generatedAt=${generatedAt}`,
    `sourceId=${sourceId}`,
    `sourceVersion=${sourceVersion}`,
    `url=${filterUrl}`,
    `sha256=${sha256}`,
    `minimumRules=${minimumRules}`
  ].join('\n') + '\n';
  const manifestBytes = Buffer.from(manifestText, 'utf8');
  if (manifestBytes.length > MAX_MANIFEST_BYTES) throw new Error('Generated manifest exceeds 16 KiB.');

  const signature = rsaSign('RSA-SHA256', manifestBytes, signingKey).toString('base64');
  const signaturePath = path.join(outputDir, `${sourceId}.${sourceVersion}.${generatedAt}.sig`);
  await atomicWrite(signaturePath, Buffer.from(signature, 'ascii'));
  // Publish manifest last: it is the commit marker consumed by clients.
  await atomicWrite(manifestPath, manifestBytes);
  return {
    sourceId, sourceVersion, generatedAt, sha256,
    manifestPath, signaturePath, filterPath, publicKey
  };
}

/**
 * Remove a source lock only when its pathname still identifies the file this
 * publisher opened. This avoids deleting a replacement owner's lock during
 * cleanup after a concurrent unlink/recreate operation.
 */
export async function removeOwnedPublishLock(lockHandle, lockPath) {
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

/**
 * Serialize manifest publication for each source in a shared output directory.
 * Stale locks are intentionally not auto-recovered; an operator must verify
 * that no publisher is running before removing one manually.
 */
export async function publishManifest(options, env = process.env) {
  const sourceId = options['source-id'];
  if (typeof sourceId !== 'string' || sourceId === '.' || sourceId === '..' ||
      !/^[a-zA-Z0-9_-]{1,64}$/.test(sourceId)) {
    throw new Error('source-id must contain 1-64 letters, digits, underscore or hyphen.');
  }
  const outputDir = path.resolve(options['output-dir'] || 'dist');
  await mkdir(outputDir, { recursive: true });
  const lockPath = path.join(outputDir, `.${sourceId}.publish.lock`);
  let lock;
  try {
    lock = await open(lockPath, 'wx', 0o600);
  } catch (error) {
    if (error.code === 'EEXIST') {
      throw new Error(`Publication for source "${sourceId}" is already locked; refusing concurrent publish.`);
    }
    throw error;
  }
  try {
    await lock.writeFile(JSON.stringify({ pid: process.pid, acquiredAt: new Date().toISOString() }) + '\\n');
    return await publishManifestUnlocked(options, env);
  } finally {
    try {
      await removeOwnedPublishLock(lock, lockPath);
    } finally {
      await lock.close();
    }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try {
    const options = argsFrom(process.argv.slice(2));
    if (options.help) {
      process.stdout.write('Usage: node scripts/publish-manifest.mjs --source-id adguard --source-version 2026.11.15 --input ./filter.txt --base-url https://filters.example.com --minimum-rules 1000 [--output-dir dist] [--public-key-out ./public-key.x509.b64]\n');
    } else {
      const result = await publishManifest(options);
      process.stdout.write(JSON.stringify(result, null, 2) + '\n');
    }
  } catch (error) {
    process.stderr.write(`Manifest publish failed: ${error.message}\n`);
    process.exitCode = 1;
  }
}
