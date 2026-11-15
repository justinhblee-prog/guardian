import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign as rsaSign, verify } from 'node:crypto';
import { mkdtemp, mkdir, readFile, rename, rm, symlink, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

process.env.NODE_ENV = 'test';
const root = await mkdtemp(path.join(os.tmpdir(), 'guardian-filter-server-'));
process.env.GUARDIAN_FILTER_DIST_DIR = root;
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const sourceId = 'adguard';
const version = 'test-v1';
const generatedAt = Math.floor(Date.now() / 1000);
const content = Buffer.from('||ads.example^\n', 'utf8');
const manifest = Buffer.from([
  'manifestVersion=1',
  `generatedAt=${generatedAt}`,
  `sourceId=${sourceId}`,
  `sourceVersion=${version}`,
  `url=https://filters.example.test/filters/${sourceId}/${version}.txt`,
  'sha256=' + (await import('node:crypto')).createHash('sha256').update(content).digest('hex'),
  'minimumRules=1',
  ''
].join('\n'));
const signature = rsaSign('RSA-SHA256', manifest, privateKey).toString('base64');
await mkdir(path.join(root, 'filters', sourceId), { recursive: true });
await writeFile(path.join(root, `${sourceId}.manifest`), manifest);
await writeFile(path.join(root, `${sourceId}.${version}.${generatedAt}.sig`), signature);
await writeFile(path.join(root, 'filters', sourceId, `${version}.txt`), content);
const { server } = await import('../src/server.mjs');
let baseUrl;

before(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  await rm(root, { recursive: true, force: true });
});

test('health endpoint identifies the distribution service', async () => {
  const response = await fetch(`${baseUrl}/health`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, service: 'guardian-filter-distribution' });
});

test('manifest endpoint returns exact bytes and detached signature header', async () => {
  const response = await fetch(`${baseUrl}/${sourceId}.manifest`);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(Buffer.from(await response.arrayBuffer()), manifest);
  const receivedSignature = response.headers.get('x-guardian-signature');
  assert.equal(receivedSignature, signature);
  assert.equal(verify('RSA-SHA256', manifest, publicKey, Buffer.from(receivedSignature, 'base64')), true);
});

test('versioned filter endpoint is immutable-cacheable and returns exact bytes', async () => {
  const response = await fetch(`${baseUrl}/filters/${sourceId}/${version}.txt`);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'public, max-age=31536000, immutable');
  assert.deepEqual(Buffer.from(await response.arrayBuffer()), content);
});

test('server rejects unknown sources, path traversal, query strings and non-GET methods', async () => {
  assert.equal((await fetch(`${baseUrl}/missing.manifest`)).status, 404);
  assert.equal((await fetch(`${baseUrl}/adguard.manifest?download=1`)).status, 400);
  assert.equal((await fetch(`${baseUrl}/health`, { method: 'POST' })).status, 405);
  assert.equal((await fetch(`${baseUrl}/filters/..%2F..%2Fetc/passwd.txt`)).status, 404);
});


test('server applies bounded HTTP connection settings and rejects dot-segment source IDs', async () => {
  assert.equal(server.headersTimeout, 10_000);
  assert.equal(server.requestTimeout, 15_000);
  assert.equal(server.keepAliveTimeout, 5_000);
  assert.equal(server.maxHeadersCount, 32);
  assert.equal((await fetch(`${baseUrl}/...manifest`)).status, 404);
  assert.equal((await fetch(`${baseUrl}/filters/%2e%2e/test-v1.txt`)).status, 404);
});

test('manifest endpoint rejects non-canonical or implausible RSA signatures', async () => {
  const signaturePath = path.join(root, `${sourceId}.${version}.${generatedAt}.sig`);
  try {
    await writeFile(signaturePath, 'not-a-signature');
    assert.equal((await fetch(`${baseUrl}/${sourceId}.manifest`)).status, 404);
    await writeFile(signaturePath, Buffer.from('A'.repeat(172), 'ascii'));
    assert.equal((await fetch(`${baseUrl}/${sourceId}.manifest`)).status, 404);
  } finally {
    await writeFile(signaturePath, signature);
  }
});



test('server rejects oversized manifest and filter files before returning content', async () => {
  const manifestPath = path.join(root, 'oversized.manifest');
  const filterDir = path.join(root, 'filters', 'oversized');
  await mkdir(filterDir, { recursive: true });
  await writeFile(manifestPath, Buffer.alloc(16 * 1024 + 1, 0x61));
  await writeFile(path.join(filterDir, 'v1.txt'), Buffer.alloc(10 * 1024 * 1024 + 1, 0x61));
  try {
    assert.equal((await fetch(`${baseUrl}/oversized.manifest`)).status, 404);
    assert.equal((await fetch(`${baseUrl}/filters/oversized/v1.txt`)).status, 404);
  } finally {
    await rm(manifestPath, { force: true });
    await rm(filterDir, { recursive: true, force: true });
  }
});

test('server rejects final-component symlinks for manifests and filters as not found', async () => {
  const manifestPath = path.join(root, `${sourceId}.manifest`);
  const manifestTarget = path.join(root, `${sourceId}.manifest.original`);
  const filterPath = path.join(root, 'filters', sourceId, `${version}.txt`);
  const filterTarget = path.join(root, 'filters', sourceId, `${version}.txt.original`);

  await rename(manifestPath, manifestTarget);
  await rename(filterPath, filterTarget);
  try {
    await symlink(manifestTarget, manifestPath);
    await symlink(filterTarget, filterPath);
    assert.equal((await fetch(`${baseUrl}/${sourceId}.manifest`)).status, 404);
    assert.equal((await fetch(`${baseUrl}/filters/${sourceId}/${version}.txt`)).status, 404);
  } finally {
    await rm(manifestPath, { force: true });
    await rm(filterPath, { force: true });
    await rename(manifestTarget, manifestPath);
    await rename(filterTarget, filterPath);
  }
});

test('server rejects a symlinked parent directory for versioned filters', async () => {
  const filtersPath = path.join(root, 'filters');
  const realFiltersPath = path.join(root, 'filters-real');
  const externalFiltersPath = path.join(root, 'external-filters');
  await mkdir(path.join(externalFiltersPath, sourceId), { recursive: true });
  await writeFile(path.join(externalFiltersPath, sourceId, `${version}.txt`), content);
  await rename(filtersPath, realFiltersPath);
  try {
    await symlink(externalFiltersPath, filtersPath, 'dir');
    assert.equal((await fetch(`${baseUrl}/filters/${sourceId}/${version}.txt`)).status, 404);
  } finally {
    await rm(filtersPath, { force: true, recursive: true });
    await rename(realFiltersPath, filtersPath);
    await rm(externalFiltersPath, { recursive: true, force: true });
  }
});
