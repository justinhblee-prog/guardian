import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { updateFilters } from '../scripts/update-filters.mjs';

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
        sources: [{ id: 'adguard', url: 'https://upstream.example/filter.txt', minimumRules: 2 }]
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
    const signatureName = (await import('node:fs/promises')).readdir(path.join(root, 'dist'));
    const files = await signatureName;
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
        { id: 'bad', url: 'http://upstream.example/filter.txt', minimumRules: 1 }
      ] },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem }
    }), /HTTPS/);
    await assert.rejects(updateFilters({
      config: { baseUrl: 'https://filters.example.com', outputDir: root, tempDir: root, sources: [
        { id: 'short', url: 'https://upstream.example/filter.txt', minimumRules: 2 }
      ] },
      env: { GUARDIAN_FILTER_SIGNING_PRIVATE_KEY: pem },
      fetchImpl: async () => new Response('||one.example^\\n', { status: 200 })
    }), /minimum is 2/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
