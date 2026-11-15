import test from 'node:test';
import assert from 'node:assert/strict';
import { createPostgresRateLimiter, createRateLimiter } from '../src/rate-limit.mjs';

test('allows requests up to configured limit and rejects the next request', () => {
  let now = 10_000;
  const limit = createRateLimiter({ windowMs: 60_000, maxRequests: 2, now: () => now });
  assert.deepEqual(limit('client-a'), { allowed: true, limit: 2, remaining: 1, retryAfterSeconds: 60 });
  assert.deepEqual(limit('client-a'), { allowed: true, limit: 2, remaining: 0, retryAfterSeconds: 60 });
  assert.equal(limit('client-a').allowed, false);
  assert.equal(limit('client-b').allowed, true);
});

test('resets expired windows and handles clock moving backwards', () => {
  let now = 10_000;
  const limit = createRateLimiter({ windowMs: 1_000, maxRequests: 1, now: () => now });
  assert.equal(limit('client').allowed, true);
  assert.equal(limit('client').allowed, false);
  now += 1_000;
  assert.equal(limit('client').allowed, true);
  now = 500;
  assert.equal(limit('client').allowed, true);
});

test('bounds memory growth when many unique clients arrive', () => {
  let now = 10_000;
  const limit = createRateLimiter({ windowMs: 60_000, maxRequests: 1, maxEntries: 2, now: () => now });
  assert.equal(limit('a').allowed, true);
  assert.equal(limit('b').allowed, true);
  assert.equal(limit('c').allowed, true);
  assert.equal(limit('c').allowed, false);
  now += 60_000;
  assert.equal(limit('d').allowed, true);
});

test('PostgreSQL limiter propagates store outages so API middleware can fail closed', async () => {
  const limiter = createPostgresRateLimiter({
    query: async () => { throw new Error('database unavailable'); }
  });
  await assert.rejects(limiter('client-a'), /database unavailable/);
});
