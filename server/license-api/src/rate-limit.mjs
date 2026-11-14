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
