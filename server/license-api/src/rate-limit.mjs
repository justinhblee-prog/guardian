/**
 * Process-local fixed-window limiter. Deployments with multiple instances must
 * additionally enforce shared limits at the HTTPS proxy/WAF layer.
 */
export function createRateLimiter({
  windowMs = 60_000,
  maxRequests = 30,
  maxEntries = 10_000,
  now = () => Date.now()
} = {}) {
  if (!Number.isInteger(windowMs) || windowMs < 1) throw new TypeError('windowMs must be a positive integer');
  if (!Number.isInteger(maxRequests) || maxRequests < 1) throw new TypeError('maxRequests must be a positive integer');
  if (!Number.isInteger(maxEntries) || maxEntries < 1) throw new TypeError('maxEntries must be a positive integer');
  const buckets = new Map();

  return function check(key) {
    const timestamp = now();
    let bucket = buckets.get(key);
    if (!bucket || timestamp - bucket.startedAt >= windowMs || timestamp < bucket.startedAt) {
      bucket = { startedAt: timestamp, count: 0 };
      buckets.delete(key);
      buckets.set(key, bucket);
    }
    bucket.count += 1;

    if (buckets.size > maxEntries) {
      for (const [entryKey, entry] of buckets) {
        if (timestamp - entry.startedAt >= windowMs || timestamp < entry.startedAt) buckets.delete(entryKey);
        if (buckets.size <= maxEntries) break;
      }
      while (buckets.size > maxEntries) buckets.delete(buckets.keys().next().value);
    }

    const remaining = Math.max(0, maxRequests - bucket.count);
    const retryAfterSeconds = Math.max(1, Math.ceil((bucket.startedAt + windowMs - timestamp) / 1000));
    return {
      allowed: bucket.count <= maxRequests,
      limit: maxRequests,
      remaining,
      retryAfterSeconds
    };
  };
}


/**
 * PostgreSQL-backed fixed-window limiter shared by every API instance using
 * the same database. The upsert serializes concurrent requests for one key.
 */
export function createPostgresRateLimiter(pool, {
  windowMs = 60_000,
  maxRequests = 30,
  cleanupEvery = 256
} = {}) {
  if (!pool || typeof pool.query !== 'function') throw new TypeError('pool.query is required');
  if (!Number.isInteger(windowMs) || windowMs < 1) throw new TypeError('windowMs must be a positive integer');
  if (!Number.isInteger(maxRequests) || maxRequests < 1) throw new TypeError('maxRequests must be a positive integer');
  if (!Number.isInteger(cleanupEvery) || cleanupEvery < 1) throw new TypeError('cleanupEvery must be a positive integer');

  let calls = 0;
  return async function checkShared(key) {
    const result = await pool.query(
      `INSERT INTO api_rate_limit_buckets(bucket_key, window_started_at, request_count)
       VALUES($1, clock_timestamp(), 1)
       ON CONFLICT (bucket_key) DO UPDATE SET
         window_started_at = CASE
           WHEN api_rate_limit_buckets.window_started_at +
             ($2::bigint * interval '1 millisecond') <= clock_timestamp()
           THEN clock_timestamp()
           ELSE api_rate_limit_buckets.window_started_at
         END,
         request_count = CASE
           WHEN api_rate_limit_buckets.window_started_at +
             ($2::bigint * interval '1 millisecond') <= clock_timestamp()
           THEN 1
           ELSE api_rate_limit_buckets.request_count + 1
         END
       RETURNING request_count, window_started_at,
         GREATEST(1, CEIL(EXTRACT(EPOCH FROM (
           window_started_at + ($2::bigint * interval '1 millisecond') - clock_timestamp()
         ))))::int AS retry_after_seconds`,
      [key, windowMs]
    );
    const bucket = result.rows[0];
    if (!bucket) throw new Error('shared_rate_limit_bucket_missing');
    calls += 1;
    if (calls % cleanupEvery === 0) {
      // Remove expired keys periodically so client churn cannot grow the table forever.
      await pool.query(
        `DELETE FROM api_rate_limit_buckets
         WHERE window_started_at + ($1::bigint * interval '1 millisecond') <= clock_timestamp()`,
        [windowMs]
      );
    }
    const count = Number(bucket.request_count);
    return {
      allowed: count <= maxRequests,
      limit: maxRequests,
      remaining: Math.max(0, maxRequests - count),
      retryAfterSeconds: Math.max(1, Number(bucket.retry_after_seconds) || 1)
    };
  };
}
