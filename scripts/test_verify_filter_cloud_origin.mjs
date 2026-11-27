import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import { mkdtemp, mkdir, writeFile, symlink, rm, readFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { verifyFilterCloudOrigin } from './verify_filter_cloud_origin.mjs';

const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const publicKeyBase64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');

async function fixture(options = {}) {
  const root = await mkdtemp(path.join(os.tmpdir(), 'guardian-filter-origin-'));
  const sourceId = 'adguard';
  const sourceVersion = '2026.11.24.1';
  const generatedAt = options.generatedAt ?? Math.floor(Date.now() / 1000) - 60;
  const filterText = options.filterText ?? '! Guardian filter fixture\n||ads.example^\n||tracker.example^\n';
  const filter = Buffer.from(filterText, 'utf8');
  const expectedUrl = 'https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data/filters/' +
    sourceId + '/' + sourceVersion + '.txt';
  const values = [
    'manifestVersion=1',
    'generatedAt=' + generatedAt,
    'sourceId=' + sourceId,
    'sourceVersion=' + sourceVersion,
    'url=' + (options.url ?? expectedUrl),
    'sha256=' + createHash('sha256').update(filter).digest('hex'),
    'minimumRules=' + (options.minimumRules ?? 2),
  ];
  if (options.duplicateField) values.push('sourceVersion=duplicate');
  const manifest = Buffer.from(values.join('\n') + '\n', 'utf8');
  const signature = sign('RSA-SHA256', manifest, privateKey);
  const signatureText = options.badSignature
    ? Buffer.alloc(signature.length, 0x5a).toString('base64')
    : signature.toString('base64');

  await mkdir(path.join(root, 'filters', sourceId), { recursive: true });
  await writeFile(path.join(root, 'guardian-filter-public-key.x509.b64'), publicKeyBase64 + '\n');
  await writeFile(path.join(root, sourceId + '.manifest'), manifest);
  await writeFile(path.join(root, sourceId + '.' + sourceVersion + '.' + generatedAt + '.sig'), signatureText + '\n');
  await writeFile(path.join(root, 'filters', sourceId, sourceVersion + '.txt'), filter);
  if (options.extraFile) await writeFile(path.join(root, 'unexpected.txt'), 'not allowed');
  if (options.symlink) await symlink(path.join(root, sourceId + '.manifest'), path.join(root, 'manifest-link'));
  return { root, filterPath: path.join(root, 'filters', sourceId, sourceVersion + '.txt') };
}

async function withFixture(options, action) {
  const created = await fixture(options);
  try {
    await action(created);
  } finally {
    await rm(created.root, { recursive: true, force: true });
  }
}

test('accepts an exact signed Guardian cloud-origin fixture', async () => {
  await withFixture({}, async ({ root }) => {
    await assert.doesNotReject(() => verifyFilterCloudOrigin(root));
  });
});

test('rejects a manifest URL that does not identify the exact source asset', async () => {
  await withFixture({ url: 'https://attacker.example/filters/adguard.txt' }, async ({ root }) => {
    await assert.rejects(() => verifyFilterCloudOrigin(root), /Manifest metadata rejected/);
  });
});

test('rejects filter bytes that no longer match the signed manifest hash', async () => {
  await withFixture({}, async ({ root, filterPath }) => {
    await writeFile(filterPath, '||changed.example^\n');
    await assert.rejects(() => verifyFilterCloudOrigin(root), /Filter SHA-256 mismatch/);
  });
});

test('rejects signatures that do not verify against the pinned public key', async () => {
  await withFixture({ badSignature: true }, async ({ root }) => {
    await assert.rejects(() => verifyFilterCloudOrigin(root), /RSA manifest signature verification failed/);
  });
});

test('rejects manifests that claim more rules than the filter contains', async () => {
  await withFixture({ minimumRules: 3 }, async ({ root }) => {
    await assert.rejects(() => verifyFilterCloudOrigin(root), /minimumRules requires 3/);
  });
});

test('rejects duplicate manifest fields and future timestamps', async (t) => {
  await withFixture({ duplicateField: true }, async ({ root }) => {
    await assert.rejects(() => verifyFilterCloudOrigin(root), /Duplicate manifest field/);
  });
  await withFixture({ generatedAt: Math.floor(Date.now() / 1000) + 1200 }, async ({ root }) => {
    await assert.rejects(() => verifyFilterCloudOrigin(root), /Manifest metadata rejected/);
  });
});

test('rejects unexpected files and symlinks in the public cloud tree', async () => {
  await withFixture({ extraFile: true }, async ({ root }) => {
    await assert.rejects(() => verifyFilterCloudOrigin(root), /Unexpected cloud file/);
  });
  await withFixture({ symlink: true }, async ({ root }) => {
    await assert.rejects(() => verifyFilterCloudOrigin(root), /Symlink is not allowed/);
  });
});

test('keeps pull-request validation independent from the not-yet-created public branch', async () => {
  const workflow = await readFile(new URL('../.github/workflows/filter-cloud-origin.yml', import.meta.url), 'utf8');
  assert.match(workflow, /test-verifier:[\s\S]*?node --test scripts\/test_verify_filter_cloud_origin\.mjs/);
  assert.match(workflow, /verify-public-filter-origin:\s*\n\s+name: Verify signed public filter assets\s*\n\s+if: github\.event_name != 'pull_request'/);
});

test('checks verifier code from the PR merge commit or main, never from the static-data branch', async () => {
  const workflow = await readFile(new URL('../.github/workflows/filter-cloud-origin.yml', import.meta.url), 'utf8');
  assert.match(workflow, /ref: \$\{\{ github\.event_name == 'pull_request' && github\.sha \|\| 'main' \}\}/);
  assert.match(workflow, /ref: main[\s\S]*?ref: guardian-filter-data/);
});
