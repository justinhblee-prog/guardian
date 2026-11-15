import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';

process.env.NODE_ENV = 'test';
process.env.GUARDIAN_RATE_LIMIT_MAX = '2';
const { server } = await import('../src/server.mjs');
let baseUrl;

before(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
});

test('server applies bounded HTTP connection and header limits', () => {
  assert.equal(server.headersTimeout, 10_000);
  assert.equal(server.requestTimeout, 15_000);
  assert.equal(server.keepAliveTimeout, 5_000);
  assert.equal(server.maxHeadersCount, 32);
});

test('health endpoint returns no-store service status', async () => {
  const response = await fetch(`${baseUrl}/health`);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await response.json(), { ok: true, service: 'guardian-license-api' });
});

test('readiness endpoint fails closed when database and signing configuration are absent', async () => {
  const response = await fetch(`${baseUrl}/ready`);
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { ok: false, error: 'not_ready' });
});

test('malformed JSON is rejected with a client error', async () => {
  const response = await fetch(`${baseUrl}/v1/unknown`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: '{'
  });
  assert.equal(response.status, 400);
});


test('malformed UTF-8 request bodies are rejected with a client error', async () => {
  const invalidUtf8Json = Buffer.from([0x7b, 0x22, 0x78, 0x22, 0x3a, 0x22, 0xc3, 0x28, 0x22, 0x7d]);
  const response = await fetch(`${baseUrl}/v1/unknown`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: invalidUtf8Json
  });
  assert.equal(response.status, 400);
  assert.match((await response.json()).error, /UTF-8/);
});

test('rejects invalid UTF-8 scalar encodings and truncated multibyte sequences', async () => {
  const invalidSequences = [
    Buffer.from([0x80]),                         // isolated continuation byte
    Buffer.from([0xc0, 0xaf]),                   // overlong encoding
    Buffer.from([0xe2, 0x82]),                   // truncated three-byte sequence
    Buffer.from([0xed, 0xa0, 0x80]),             // UTF-8 encoded surrogate
    Buffer.from([0xf4, 0x90, 0x80, 0x80])        // code point above U+10FFFF
  ];
  const prefix = Buffer.from('{"value":"');
  const suffix = Buffer.from('"}');

  for (const invalid of invalidSequences) {
    const response = await fetch(`${baseUrl}/v1/unknown`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: Buffer.concat([prefix, invalid, suffix])
    });
    assert.equal(response.status, 400);
    assert.match((await response.json()).error, /UTF-8/);
  }
});

test('oversized request bodies are rejected', async () => {
  const response = await fetch(`${baseUrl}/v1/unknown`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ padding: 'x'.repeat(17 * 1024) })
  });
  assert.equal(response.status, 413);
});

test('license endpoints return 429 and Retry-After after the configured request limit', async () => {
  const request = () => fetch(`${baseUrl}/v1/licenses/activate`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: '{}'
  });
  assert.equal((await request()).status, 503); // DB is intentionally absent in this isolated HTTP test.
  assert.equal((await request()).status, 503);
  const limited = await request();
  assert.equal(limited.status, 429);
  assert.ok(Number(limited.headers.get('retry-after')) >= 1);
  assert.equal(limited.headers.get('ratelimit-remaining'), '0');
});
