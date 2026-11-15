import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, verify } from 'node:crypto';
import { mkdtemp, mkdir, readFile, rm, symlink, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { publishManifest } from '../scripts/publish-manifest.mjs';

let root;
let privateKey;
let publicKey;

before(async () => {
  root = await mkdtemp(path.join(os.tmpdir(), 'guardian-filter-publisher-'));
  const pair = generateKeyPairSync('rsa', { modulusLength: 2048 });
  privateKey = pair.privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  publicKey = pair.publicKey;
});

after(async () => {
  await rm(root, { recursive: true, force: true });
});

test('publisher creates a detached RSA signature and a hash-bound immutable filter URL', async () => {
  const input = path.join(root, 'filter.txt');
  const outputDir = path.join(root, 'dist');
  const publicKeyPath = path.join(root, 'public-key.x509.b64');
  const content = Buffer.from('||ads.example^\n||tracker.example^\n', 'utf8');
  await writeFile(input, content);

  const result = await publishManifest({
    'source-id': 'adguard',
    'source-version': '2026.11.15',
    input,
    'base-url': 'https://filters.example.com/guardian',
    'minimum-rules': '2',
    'output-dir': outputDir,
    'public-key-out': publicKeyPath
  }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey });

  const manifestBytes = await readFile(result.manifestPath);
  const signature = (await readFile(result.signaturePath, 'ascii')).trim();
  assert.equal(verify('RSA-SHA256', manifestBytes, publicKey, Buffer.from(signature, 'base64')), true);
  const manifest = Object.fromEntries(manifestBytes.toString('utf8').trim().split('\n').map(line => {
    const index = line.indexOf('=');
    return [line.slice(0, index), line.slice(index + 1)];
  }));
  assert.equal(manifest.sourceId, 'adguard');
  assert.equal(manifest.sourceVersion, '2026.11.15');
  assert.equal(manifest.minimumRules, '2');
  assert.equal(manifest.url, 'https://filters.example.com/guardian/filters/adguard/2026.11.15.txt');
  assert.equal(manifest.sha256, createHash('sha256').update(content).digest('hex'));
  assert.deepEqual(await readFile(result.filterPath), content);
  assert.equal((await readFile(publicKeyPath, 'ascii')).trim(), result.publicKey);
});

test('publisher advances generatedAt even when publishing twice within one second', async () => {
  const input = path.join(root, 'repeat.txt');
  const outputDir = path.join(root, 'repeat-dist');
  await writeFile(input, '||one.example^\n');
  const options = {
    'source-id': 'security',
    'source-version': 'v1',
    input,
    'base-url': 'https://filters.example.com',
    'minimum-rules': '1',
    'output-dir': outputDir
  };
  const first = await publishManifest(options, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey });
  const second = await publishManifest(options, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey });
  assert.ok(second.generatedAt > first.generatedAt);
});

test('publisher refuses to overwrite immutable version with different content', async () => {
  const input = path.join(root, 'immutable.txt');
  const outputDir = path.join(root, 'immutable-dist');
  await writeFile(input, '||first.example^\n');
  const options = {
    'source-id': 'adguard',
    'source-version': 'immutable-1',
    input,
    'base-url': 'https://filters.example.com',
    'minimum-rules': '1',
    'output-dir': outputDir
  };
  await publishManifest(options, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey });
  await writeFile(input, '||changed.example^\n');
  await assert.rejects(
    publishManifest(options, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey }),
    /immutable sourceVersion/
  );
});

test('publisher rejects RSA signing keys below 2048 bits', async () => {
  const weakKey = generateKeyPairSync('rsa', { modulusLength: 1024 }).privateKey
    .export({ type: 'pkcs8', format: 'pem' }).toString();
  const input = path.join(root, 'weak-key.txt');
  await writeFile(input, '||weak.example^\n');
  await assert.rejects(
    publishManifest({
      'source-id': 'adguard',
      'source-version': 'weak-key',
      input,
      'base-url': 'https://filters.example.com',
      'minimum-rules': '1',
      'output-dir': path.join(root, 'weak-key-dist')
    }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: weakKey }),
    /between 2048 and 4096 bits/
  );
});

test('publisher refuses to inherit a corrupt future anti-rollback timestamp', async () => {
  const input = path.join(root, 'future.txt');
  const outputDir = path.join(root, 'future-dist');
  await mkdir(outputDir, { recursive: true });
  await writeFile(input, '||future.example^\n');
  await writeFile(path.join(outputDir, 'adguard.manifest'), 'generatedAt=9999999999\n');
  await assert.rejects(
    publishManifest({
      'source-id': 'adguard',
      'source-version': 'v1',
      input,
      'base-url': 'https://filters.example.com',
      'minimum-rules': '1',
      'output-dir': outputDir
    }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey }),
    /identity or generatedAt is invalid/
  );
  await assert.rejects(readFile(path.join(outputDir, 'filters', 'adguard', 'v1.txt')));
});

test('publisher rejects insecure URLs, path segments and missing signing keys', async () => {
  const input = path.join(root, 'invalid.txt');
  await writeFile(input, '||example.test^\n');
  const base = {
    'source-id': 'adguard',
    'source-version': 'v1',
    input,
    'base-url': 'http://filters.example.com',
    'minimum-rules': '1',
    'output-dir': path.join(root, 'invalid-dist')
  };
  await assert.rejects(publishManifest(base, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey }), /HTTPS/);
  for (const sourceId of ['..', '.', 'source.name', 'source/name', '../escape']) {
    await assert.rejects(
      publishManifest({ ...base, 'source-id': sourceId, 'base-url': 'https://filters.example.com' }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey }),
      /source-id/
    );
  }
  await assert.rejects(
    publishManifest({ ...base, 'base-url': 'https://filters.example.com' }, {}),
    /GUARDIAN_FILTER_SIGNING_PRIVATE_KEY/
  );
});

test('publisher refuses a symlink at an immutable filter version path', async () => {
  const input = path.join(root, 'symlink-input.txt');
  const outputDir = path.join(root, 'symlink-dist');
  const immutableDir = path.join(outputDir, 'filters', 'adguard');
  const target = path.join(root, 'trusted-target.txt');
  await mkdir(immutableDir, { recursive: true });
  await writeFile(input, '||same-content.example^\n');
  await writeFile(target, '||same-content.example^\n');
  await symlink(target, path.join(immutableDir, 'v1.txt'));
  await assert.rejects(
    publishManifest({
      'source-id': 'adguard',
      'source-version': 'v1',
      input,
      'base-url': 'https://filters.example.com',
      'minimum-rules': '1',
      'output-dir': outputDir
    }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey })
  );
});

test('publisher refuses a symlinked previous manifest during anti-rollback checks', async () => {
  const input = path.join(root, 'previous-manifest-input.txt');
  const outputDir = path.join(root, 'previous-manifest-dist');
  const target = path.join(root, 'external-previous-manifest.txt');
  await mkdir(outputDir, { recursive: true });
  await writeFile(input, '||manifest-link.example^\n');
  await writeFile(target, 'generatedAt=1\n');
  await symlink(target, path.join(outputDir, 'adguard.manifest'));
  await assert.rejects(
    publishManifest({
      'source-id': 'adguard',
      'source-version': 'v2',
      input,
      'base-url': 'https://filters.example.com',
      'minimum-rules': '1',
      'output-dir': outputDir
    }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey })
  );
});

test('publisher rejects an oversized previous manifest before parsing it', async () => {
  const input = path.join(root, 'oversized-manifest-input.txt');
  const outputDir = path.join(root, 'oversized-manifest-dist');
  await mkdir(outputDir, { recursive: true });
  await writeFile(input, '||oversized-manifest.example^\n');
  await writeFile(path.join(outputDir, 'adguard.manifest'), 'x'.repeat(16 * 1024 + 1));
  await assert.rejects(
    publishManifest({
      'source-id': 'adguard',
      'source-version': 'v3',
      input,
      'base-url': 'https://filters.example.com',
      'minimum-rules': '1',
      'output-dir': outputDir
    }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey }),
    /16 KiB/
  );
});


test('publisher fails before manifest commit when optional public-key output cannot be written', async () => {
  const input = path.join(root, 'public-key-output-failure.txt');
  const outputDir = path.join(root, 'public-key-output-failure-dist');
  const publicKeyDirectory = path.join(root, 'public-key-output-is-directory');
  await writeFile(input, '||key-output-failure.example^\n');
  await mkdir(publicKeyDirectory, { recursive: true });
  await assert.rejects(publishManifest({
    'source-id': 'security',
    'source-version': 'public-key-output-failure',
    input,
    'base-url': 'https://filters.example.com',
    'minimum-rules': '1',
    'output-dir': outputDir,
    'public-key-out': publicKeyDirectory
  }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey }));
  await assert.rejects(readFile(path.join(outputDir, 'security.manifest')));
  await assert.rejects(readFile(path.join(outputDir, 'filters', 'security', 'public-key-output-failure.txt')));
});


test('publisher refuses a previous manifest belonging to a different source', async () => {
  const input = path.join(root, 'wrong-identity-input.txt');
  const outputDir = path.join(root, 'wrong-identity-dist');
  await mkdir(outputDir, { recursive: true });
  await writeFile(input, '||wrong-identity.example^\n');
  await writeFile(path.join(outputDir, 'adguard.manifest'), 'generatedAt=100\nsourceId=security\n');
  await assert.rejects(publishManifest({
    'source-id': 'adguard',
    'source-version': 'wrong-identity-v1',
    input,
    'base-url': 'https://filters.example.com',
    'minimum-rules': '1',
    'output-dir': outputDir
  }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey }), /identity or generatedAt is invalid/);
  await assert.rejects(readFile(path.join(outputDir, 'filters', 'adguard', 'wrong-identity-v1.txt')));
});

test('publisher fails closed when another process holds the source publication lock', async () => {
  const input = path.join(root, 'locked.txt');
  const outputDir = path.join(root, 'locked-dist');
  await mkdir(outputDir, { recursive: true });
  await writeFile(input, '||locked.example^\n');
  const lockPath = path.join(outputDir, '.adguard.publish.lock');
  await writeFile(lockPath, JSON.stringify({ pid: 99999 }), { flag: 'wx' });
  await assert.rejects(
    publishManifest({
      'source-id': 'adguard',
      'source-version': 'locked-v1',
      input,
      'base-url': 'https://filters.example.com',
      'minimum-rules': '1',
      'output-dir': outputDir
    }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey }),
    /already locked; refusing concurrent publish/
  );
  await assert.rejects(readFile(path.join(outputDir, 'adguard.manifest')), { code: 'ENOENT' });
  await rm(lockPath, { force: true });
});

test('publisher releases its source lock when validation fails after acquisition', async () => {
  const input = path.join(root, 'failed-lock.txt');
  const outputDir = path.join(root, 'failed-lock-dist');
  await writeFile(input, '||failed-lock.example^\n');
  await assert.rejects(
    publishManifest({
      'source-id': 'failed',
      'source-version': 'failed-v1',
      input,
      'base-url': 'https://filters.example.com',
      'minimum-rules': '1',
      'output-dir': outputDir
    }, {}),
    /GUARDIAN_FILTER_SIGNING_PRIVATE_KEY is required/
  );
  await assert.rejects(readFile(path.join(outputDir, '.failed.publish.lock')), { code: 'ENOENT' });
});

test('publisher releases its source lock after a successful publication', async () => {
  const input = path.join(root, 'unlock.txt');
  const outputDir = path.join(root, 'unlock-dist');
  await writeFile(input, '||unlock.example^\n');
  await publishManifest({
    'source-id': 'security',
    'source-version': 'unlock-v1',
    input,
    'base-url': 'https://filters.example.com',
    'minimum-rules': '1',
    'output-dir': outputDir
  }, { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey });
  await assert.rejects(readFile(path.join(outputDir, '.security.publish.lock')), { code: 'ENOENT' });
});

test('concurrent publisher invocations for one source allow exactly one writer', async () => {
  const firstInput = path.join(root, 'concurrent-first.txt');
  const secondInput = path.join(root, 'concurrent-second.txt');
  const outputDir = path.join(root, 'concurrent-dist');
  await writeFile(firstInput, '||first-writer.example^\n');
  await writeFile(secondInput, '||second-writer.example^\n');
  const options = input => ({
    'source-id': 'concurrent',
    'source-version': 'same-version',
    input,
    'base-url': 'https://filters.example.com',
    'minimum-rules': '1',
    'output-dir': outputDir
  });

  const results = await Promise.allSettled([
    publishManifest(options(firstInput), { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey }),
    publishManifest(options(secondInput), { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: privateKey })
  ]);
  assert.equal(results.filter(result => result.status === 'fulfilled').length, 1);
  assert.equal(results.filter(result => result.status === 'rejected').length, 1);
  const rejected = results.find(result => result.status === 'rejected');
  assert.match(rejected.reason.message, /already locked; refusing concurrent publish/);
  const manifest = await readFile(path.join(outputDir, 'concurrent.manifest'), 'utf8');
  assert.match(manifest, /^sourceVersion=same-version$/m);
  const published = await readFile(path.join(outputDir, 'filters', 'concurrent', 'same-version.txt'), 'utf8');
  assert.ok(published === '||first-writer.example^\n' || published === '||second-writer.example^\n');
});
