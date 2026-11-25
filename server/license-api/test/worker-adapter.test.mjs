import test from 'node:test';
import assert from 'node:assert/strict';
import { handleWorkerRequest } from '../src/worker.mjs';

test('Worker adapter exposes the shared health endpoint without database secrets', async () => {
  const response = await handleWorkerRequest(new Request('https://guardian.example/health'), {});
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, service: 'guardian-license-api' });
});

test('Worker adapter rejects oversized request bodies before invoking the API', async () => {
  const body = 'x'.repeat(16 * 1024 + 1);
  const response = await handleWorkerRequest(new Request('https://guardian.example/v1/licenses/activate', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body
  }), {});
  assert.equal(response.status, 413);
});

test('Worker adapter serves the existing admin page through the static asset binding', async () => {
  const assets = {
    fetch: async request => new Response(new URL(request.url).pathname, { status: 200 })
  };
  const response = await handleWorkerRequest(new Request('https://guardian.example/admin'), { ASSETS: assets });
  assert.equal(response.status, 200);
  assert.equal(await response.text(), '/index.html');
});
