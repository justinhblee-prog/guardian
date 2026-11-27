import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { mkdir, mkdtemp, open, readFile, readdir, rename, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { updateFilters } from '../scripts/update-filters.mjs';
import { removeOwnedPublishLock } from '../scripts/publish-manifest.mjs';

test('updater fetches HTTPS filters, checks minimum rules and publishes signed content', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'guardian-filter-update-'));
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  const content = Buffer.from('||one.example^\n||two.example^\n');
  let requestedUrl;
  try {
    const result = await updateFilters({
      config: {
        baseUrl: 'https://filters.example.com',
        outputDir: path.join(root, 'dist'),
        tempDir: path.join(root, 'tmp'),
        sources: [{ id: 'adguard', url: 'https://upstream.example/filter.txt', minimumRules: 2, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }]
      },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
      now: new Date('2026-11-15T00:00:00Z'),
      fetchImpl: async url => {
        requestedUrl = url.href;
        return new Response(content, { status: 200, headers: { 'content-length': String(content.length) } });
      }
    });
    assert.equal(requestedUrl, 'https://upstream.example/filter.txt');
    assert.equal(result.length, 1);
    assert.equal(result[0].ruleCount, 2);
    const manifest = await readFile(path.join(root, 'dist', 'adguard.manifest'));
    const files = await readdir(path.join(root, 'dist'));
    const sigFile = files.find(name => name.endsWith('.sig'));
    const signature = (await readFile(path.join(root, 'dist', sigFile), 'ascii')).trim();
    assert.equal(verify('RSA-SHA256', manifest, publicKey, Buffer.from(signature, 'base64')), true);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test('updater rejects non-HTTPS sources and underfilled upstream responses', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'guardian-filter-update-'));
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  try {
    await assert.rejects(updateFilters({
      config: { baseUrl: 'https://filters.example.com', outputDir: root, sources: [
        { id: 'bad', url: 'http://upstream.example/filter.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }
      ] },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem }
    }), /HTTPS/);
    await assert.rejects(updateFilters({
      config: { baseUrl: 'https://filters.example.com', outputDir: root, tempDir: root, sources: [
        { id: 'short', url: 'https://upstream.example/filter.txt', minimumRules: 2, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }
      ] },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
      fetchImpl: async () => new Response('||one.example^\n', { status: 200 })
    }), /minimum is 2/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test('updater rejects floating source versions and mutable CDN references before network access', async () => {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  for (const overrides of [
    { upstreamVersion: 'rolling-unpinned' },
    { upstreamVersion: 'latest' },
    { url: 'https://cdn.jsdelivr.net/gh/example/filter@latest/list.txt' },
    { url: 'https://cdn.jsdelivr.net/gh/example/filter@main/list.txt' }
  ]) {
    let fetchCalled = false;
    await assert.rejects(updateFilters({
      config: {
        baseUrl: 'https://filters.example.com',
        sources: [{
          id: 'floating',
          url: 'https://upstream.example/filter.txt',
          minimumRules: 1,
          upstreamRepository: 'https://upstream.example/project',
          licenseUrl: 'https://upstream.example/LICENSE',
          upstreamVersion: '1.0.0',
          thirdPartyProvenanceReviewed: true,
          complianceEvidenceRef: 'test-fixture',
          licenseId: 'LicenseRef-Test',
          redistributionApproved: true,
          redistributionApprovalRef: 'test-fixture',
          ...overrides
        }]
      },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
      fetchImpl: async () => {
        fetchCalled = true;
        return new Response('||example.test^', { status: 200 });
      }
    }), /pinned upstream version\/URL/);
    assert.equal(fetchCalled, false);
  }
});

test('updater stages every source before publishing any manifest', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'guardian-filter-stage-all-'));
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  const outputDir = path.join(root, 'dist');
  let calls = 0;
  try {
    await assert.rejects(updateFilters({
      config: {
        baseUrl: 'https://filters.example.com',
        outputDir,
        tempDir: path.join(root, 'tmp'),
        sources: [
          { id: 'first', url: 'https://upstream.example/first.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' },
          { id: 'second', url: 'https://upstream.example/second.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }
        ]
      },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
      fetchImpl: async () => {
        calls += 1;
        if (calls === 1) return new Response('||first.example^\n', { status: 200 });
        return new Response('upstream unavailable', { status: 503 });
      }
    }), /HTTP 503/);
    assert.equal(calls, 2);
    await assert.rejects(readFile(path.join(outputDir, 'first.manifest')));
    assert.equal((await readdir(path.join(root, 'tmp'))).length, 0);
    await assert.rejects(readFile(path.join(outputDir, '.guardian-update.lock')));

    // A failed batch must release its lock so a later scheduled retry can run.
    const retry = await updateFilters({
      config: {
        baseUrl: 'https://filters.example.com',
        outputDir,
        tempDir: path.join(root, 'tmp'),
        sources: [{ id: 'first', url: 'https://upstream.example/first.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }]
      },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
      fetchImpl: async () => new Response('||first.example^\n', { status: 200 })
    });
    assert.equal(retry.length, 1);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});


test('updater refuses to interleave with an active batch lock before fetching', async () => {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  const root = await mkdtemp(path.join(os.tmpdir(), 'guardian-filter-batch-lock-'));
  const outputDir = path.join(root, 'dist');
  let fetchCalled = false;
  try {
    await mkdir(outputDir, { recursive: true });
    await writeFile(path.join(outputDir, '.guardian-update.lock'), '{"pid":1}\n', { flag: 'wx' });
    await assert.rejects(updateFilters({
      config: {
        baseUrl: 'https://filters.example.com',
        outputDir,
        sources: [{ id: 'first', url: 'https://upstream.example/first.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }]
      },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
      fetchImpl: async () => {
        fetchCalled = true;
        return new Response('||first.example^\n', { status: 200 });
      }
    }), /Another filter update batch is active/);
    assert.equal(fetchCalled, false);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});


test('updater validates signing credentials before making upstream requests', async () => {
  let fetchCalled = false;
  await assert.rejects(updateFilters({
    config: {
      baseUrl: 'https://filters.example.com',
      sources: [{ id: 'first', url: 'https://upstream.example/first.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }]
    },
    env: {},
    fetchImpl: async () => {
      fetchCalled = true;
      return new Response('||first.example^', { status: 200 });
    }
  }), /GUARDIAN_FILTER_SIGNING_PRIVATE_KEY is required/);
  assert.equal(fetchCalled, false);
});


test('updater does not remove a replacement lock it no longer owns', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'guardian-filter-lock-replacement-'));
  const outputDir = path.join(root, 'dist');
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  const lockPath = path.join(outputDir, '.guardian-update.lock');
  try {
    const result = await updateFilters({
      config: {
        baseUrl: 'https://filters.example.com',
        outputDir,
        tempDir: path.join(root, 'tmp'),
        sources: [{ id: 'first', url: 'https://upstream.example/first.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }]
      },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
      fetchImpl: async () => {
        // Simulate an external actor replacing the pathname while this process
        // still holds the original lock file descriptor.
        await rm(lockPath);
        await writeFile(lockPath, 'replacement-owner\n', { flag: 'wx' });
        return new Response('||first.example^\n', { status: 200 });
      }
    });
    assert.equal(result.length, 1);
    assert.equal(await readFile(lockPath, 'utf8'), 'replacement-owner\n');
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});


test('updater rejects RSA signing keys larger than 4096 bits before network access', async () => {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 8192 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  let fetchCalled = false;
  await assert.rejects(updateFilters({
    config: {
      baseUrl: 'https://filters.example.com',
      sources: [{ id: 'first', url: 'https://upstream.example/first.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }]
    },
    env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
    fetchImpl: async () => {
      fetchCalled = true;
      return new Response('||first.example^', { status: 200 });
    }
  }), /between 2048 and 4096 bits/);
  assert.equal(fetchCalled, false);
});



test('updater refuses third-party filter redistribution without explicit legal approval before network access', async () => {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  let fetchCalled = false;
  await assert.rejects(updateFilters({
    config: {
      baseUrl: 'https://filters.example.com',
      sources: [{
        id: 'unreviewed',
        url: 'https://upstream.example/filter.txt',
        minimumRules: 1,
        upstreamRepository: 'https://upstream.example/project',
        licenseUrl: 'https://upstream.example/LICENSE',
        upstreamVersion: 'test-1',
        thirdPartyProvenanceReviewed: true,
        complianceEvidenceRef: 'test-fixture',
        licenseId: 'GPL-3.0',
        redistributionApproved: false,
        redistributionApprovalRef: ''
      }]
    },
    env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
    fetchImpl: async () => {
      fetchCalled = true;
      return new Response('||example.test^', { status: 200 });
    }
  }), /not approved for redistribution/);
  assert.equal(fetchCalled, false);
});

test('updater requires a traceable approval reference even when approval flag is true', async () => {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  let fetchCalled = false;
  await assert.rejects(updateFilters({
    config: {
      baseUrl: 'https://filters.example.com',
      sources: [{
        id: 'untraceable',
        url: 'https://upstream.example/filter.txt',
        minimumRules: 1,
        upstreamRepository: 'https://upstream.example/project',
        licenseUrl: 'https://upstream.example/LICENSE',
        upstreamVersion: 'test-1',
        thirdPartyProvenanceReviewed: true,
        complianceEvidenceRef: 'test-fixture',
        licenseId: 'GPL-3.0',
        redistributionApproved: true,
        redistributionApprovalRef: '   '
      }]
    },
    env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
    fetchImpl: async () => {
      fetchCalled = true;
      return new Response('||example.test^', { status: 200 });
    }
  }), /not approved for redistribution/);
  assert.equal(fetchCalled, false);
});

test('updater rejects excessive source counts before making network requests', async () => {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  let fetchCalled = false;
  const sources = Array.from({ length: 101 }, (_, index) => ({
    id: `source_${index}`,
    url: `https://upstream.example/${index}.txt`,
    minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture'
  }));
  await assert.rejects(updateFilters({
    config: { baseUrl: 'https://filters.example.com', sources },
    env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
    fetchImpl: async () => {
      fetchCalled = true;
      return new Response('||example.com^', { status: 200 });
    }
  }), /at most 100 filter sources/);
  assert.equal(fetchCalled, false);
});


test('updater rejects duplicate source IDs before any upstream request', async () => {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  let fetchCalled = false;
  await assert.rejects(updateFilters({
    config: {
      baseUrl: 'https://filters.example.com',
      sources: [
        { id: 'duplicate', url: 'https://upstream.example/one.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' },
        { id: 'duplicate', url: 'https://upstream.example/two.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }
      ]
    },
    env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
    fetchImpl: async () => {
      fetchCalled = true;
      return new Response('||example.test^', { status: 200 });
    }
  }), /Duplicate source id in config: duplicate/);
  assert.equal(fetchCalled, false);
});

test('updater rejects upstream bodies that disagree with Content-Length', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'guardian-filter-length-mismatch-'));
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  try {
    await assert.rejects(updateFilters({
      config: {
        baseUrl: 'https://filters.example.com',
        outputDir: path.join(root, 'dist'),
        tempDir: path.join(root, 'tmp'),
        sources: [{ id: 'length', url: 'https://upstream.example/filter.txt', minimumRules: 1, upstreamRepository: 'https://upstream.example/project', licenseUrl: 'https://upstream.example/LICENSE', upstreamVersion: 'test-1', thirdPartyProvenanceReviewed: true, complianceEvidenceRef: 'test-fixture', licenseId: 'LicenseRef-Test', redistributionApproved: true, redistributionApprovalRef: 'test-fixture' }]
      },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
      fetchImpl: async () => new Response('||example.test^\n', {
        status: 200,
        headers: { 'content-length': '999' }
      })
    }), /Content-Length mismatch/);
    await assert.rejects(readFile(path.join(root, 'dist', '.guardian-update.lock')));
    await assert.rejects(readdir(path.join(root, 'tmp')), { code: 'ENOENT' });
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});


test('publisher cleanup removes only the source lock inode it owns', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'guardian-publish-owned-lock-'));
  const lockPath = path.join(root, '.adguard.publish.lock');
  const movedPath = path.join(root, '.adguard.publish.lock.original');
  const handle = await open(lockPath, 'wx', 0o600);
  try {
    await handle.writeFile('owned-lock\\n');
    await removeOwnedPublishLock(handle, lockPath);
    await assert.rejects(readFile(lockPath), { code: 'ENOENT' });
  } finally {
    await handle.close();
    await rm(root, { recursive: true, force: true });
  }
});

test('publisher cleanup preserves a replacement source lock', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'guardian-publish-replaced-lock-'));
  const lockPath = path.join(root, '.adguard.publish.lock');
  const movedPath = path.join(root, '.adguard.publish.lock.original');
  const handle = await open(lockPath, 'wx', 0o600);
  try {
    await handle.writeFile('original-lock\\n');
    // Keep the original inode alive under another name so the filesystem
    // cannot recycle its inode for the replacement lock at the old pathname.
    await rename(lockPath, movedPath);
    await writeFile(lockPath, 'replacement-owner\\n', { flag: 'wx', mode: 0o600 });
    await removeOwnedPublishLock(handle, lockPath);
    assert.equal(await readFile(lockPath, 'utf8'), 'replacement-owner\\n');
  } finally {
    await handle.close();
    await rm(root, { recursive: true, force: true });
  }
});

test('updater rejects missing source provenance and compliance evidence before network access', async () => {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  let fetchCalled = false;
  await assert.rejects(updateFilters({
    config: {
      baseUrl: 'https://filters.example.com',
      sources: [{
        id: 'missing-provenance',
        url: 'https://upstream.example/filter.txt',
        minimumRules: 1,
        licenseId: 'GPL-3.0',
        redistributionApproved: true,
        redistributionApprovalRef: 'review-123',
        upstreamRepository: 'https://upstream.example/project',
        licenseUrl: 'https://upstream.example/LICENSE',
        upstreamVersion: '1.0.0',
        thirdPartyProvenanceReviewed: false,
        complianceEvidenceRef: ''
      }]
    },
    env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
    fetchImpl: async () => {
      fetchCalled = true;
      return new Response('||example.test^', { status: 200 });
    }
  }), /third-party provenance review or compliance evidence reference/);
  assert.equal(fetchCalled, false);
});
