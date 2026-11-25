import test from 'node:test';
import assert from 'node:assert/strict';
import { createNeonLicenseHandler } from '../functions/guardianapi/index.mjs';

const handler = createNeonLicenseHandler({
  NODE_ENV: 'test',
  DATABASE_URL: '',
  GUARDIAN_ADMIN_TOKEN: '',
  GUARDIAN_LICENSE_LOOKUP_SECRET: '',
  GUARDIAN_LICENSE_PRIVATE_KEY: ''
});

test('Neon Function serves the shared license API health endpoint', async () => {
  const response = await handler.fetch(new Request('https://guardian.example/health'));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, service: 'guardian-license-api' });
});

test('Neon Function fails readiness closed until database and signing secrets exist', async () => {
  const response = await handler.fetch(new Request('https://guardian.example/ready'));
  assert.equal(response.status, 503);
});

test('Neon Function serves admin HTML and all ES module assets', async () => {
  const html = await handler.fetch(new Request('https://guardian.example/admin'));
  assert.equal(html.status, 200);
  assert.match(await html.text(), /<html/i);

  const js = await handler.fetch(new Request('https://guardian.example/admin.js'));
  assert.equal(js.status, 200);
  assert.match(await js.text(), /adminToken/);

  const model = await handler.fetch(new Request('https://guardian.example/admin-model.mjs'));
  assert.equal(model.status, 200);
  assert.match(await model.text(), /effectiveLicenseStatus/);
});

test('Neon Function rejects oversized request bodies', async () => {
  const response = await handler.fetch(new Request('https://guardian.example/v1/licenses/activate', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: 'x'.repeat(16 * 1024 + 1)
  }));
  assert.equal(response.status, 413);
});
