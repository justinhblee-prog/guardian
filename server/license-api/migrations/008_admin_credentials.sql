-- Persist a single Guardian administrator bearer-token verifier.
-- The random token itself is never stored in PostgreSQL.
CREATE TABLE IF NOT EXISTS admin_credentials (
  singleton_id smallint PRIMARY KEY DEFAULT 1 CHECK (singleton_id = 1),
  token_hash char(64) NOT NULL CHECK (token_hash ~ '^[a-f0-9]{64}$'),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
