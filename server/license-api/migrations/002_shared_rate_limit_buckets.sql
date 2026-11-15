-- Apply to existing Guardian License API databases before deploying shared rate limiting.
BEGIN;

CREATE TABLE IF NOT EXISTS api_rate_limit_buckets (
  bucket_key text PRIMARY KEY,
  window_started_at timestamptz NOT NULL,
  request_count integer NOT NULL CHECK (request_count > 0)
);

CREATE INDEX IF NOT EXISTS api_rate_limit_buckets_window_idx
  ON api_rate_limit_buckets(window_started_at);

COMMIT;
