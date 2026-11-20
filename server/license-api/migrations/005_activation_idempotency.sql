-- Durable idempotency for mobile activation retries after response loss.
CREATE TABLE IF NOT EXISTS activation_requests (
  request_id uuid PRIMARY KEY,
  device_binding text NOT NULL,
  request_fingerprint text NOT NULL,
  response jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS activation_requests_created_idx ON activation_requests(created_at);
