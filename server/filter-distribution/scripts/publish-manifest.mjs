import { createPrivateKey, createPublicKey, randomUUID, sign as rsaSign } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
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
  await mkdir(path.dirname(filePath), { recursive: true });
  const temporary = `${filePath}.${randomUUID()}.tmp`;
  await writeFile(temporary, bytes, { flag: 'wx', mode: 0o644 });
  await rename(temporary, filePath);
}

export async function publishManifest(options, env = process.env) {
  const sourceId = options['source-id'];
  const sourceVersion = options['source-version'];
  const input = options.input;
  const baseUrl = options['base-url'];
  const minimumRules = Number(options['minimum-rules']);
  const outputDir = path.resolve(options['output-dir'] || 'dist');
  const privateKeyText = env.GUARDIAN_FILTER_SIGNING_PRIVATE_KEY;

  if (typeof sourceId !== 'string' || sourceId === '.' || sourceId === '..' || !/^[a-zA-Z0-9._-]{1,64}$/.test(sourceId)) {
    throw new Error('source-id must contain 1-64 letters, digits, dot, underscore or hyphen.');
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
  if (signingKey.asymmetricKeyType !== 'rsa' ||
      (signingKey.asymmetricKeyDetails?.modulusLength || 0) < 2048) {
    throw new Error('Signing key must be RSA with a modulus of at least 2048 bits.');
  }

  const publicBase = new URL(baseUrl);
  if (publicBase.protocol !== 'https:' || !publicBase.hostname ||
      publicBase.username || publicBase.password || publicBase.search || publicBase.hash) {
    throw new Error('--base-url must be an HTTPS origin/path without credentials, query or fragment.');
  }

  const content = await readFile(input);
  if (content.length === 0 || content.length > MAX_FILTER_BYTES || content.includes(0)) {
    throw new Error('Filter content is empty, contains NUL, or exceeds 10 MiB.');
  }
  const contentText = content.toString('utf8');
  if (!Buffer.from(contentText, 'utf8').equals(content)) {
    throw new Error('Filter content must be valid UTF-8.');
  }

  const filterRelativePath = path.join('filters', sourceId, `${sourceVersion}.txt`);
  const filterPath = path.join(outputDir, filterRelativePath);
  try {
    const existing = await readFile(filterPath);
    if (!existing.equals(content)) {
      throw new Error('An immutable sourceVersion already exists with different filter content.');
    }
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
    await atomicWrite(filterPath, content);
  }

  const manifestPath = path.join(outputDir, `${sourceId}.manifest`);
  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  let previousGeneratedAt = 0;
  try {
    const previous = await readFile(manifestPath, 'utf8');
    const match = /^generatedAt=(\d+)$/m.exec(previous);
    if (!match) throw new Error('Existing manifest has no valid generatedAt; refusing to reset anti-rollback time.');
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

  const publicKey = createPublicKey(signingKey)
    .export({ type: 'spki', format: 'der' }).toString('base64');
  if (options['public-key-out']) {
    await atomicWrite(path.resolve(options['public-key-out']), Buffer.from(publicKey + '\n', 'ascii'));
  }
  return {
    sourceId, sourceVersion, generatedAt, sha256,
    manifestPath, signaturePath, filterPath, publicKey
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try {
    const options = argsFrom(process.argv.slice(2));
    if (options.help) {
      process.stdout.write('Usage: node scripts/publish-manifest.mjs --source-id adguard --source-version 2026.11.15 --input ./filter.txt --base-url https://filters.example.com --minimum-rules 1000 [--output-dir dist] [--public-key-out ./public-key.x509.b64]\\n');
    } else {
      const result = await publishManifest(options);
      process.stdout.write(JSON.stringify(result, null, 2) + '\n');
    }
  } catch (error) {
    process.stderr.write(`Manifest publish failed: ${error.message}\\n`);
    process.exitCode = 1;
  }
}
