-- PostgreSQL schema for Guardian license issuance and device activations.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS products (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE,
  name text NOT NULL,
  entitlement text NOT NULL CHECK (entitlement IN ('pro')),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO products(code, name, entitlement)
VALUES
  ('guardian-pro', 'Guardian Pro', 'pro'),
  ('guardian-android-pro', 'Guardian Android Pro (legacy)', 'pro')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS customers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name varchar(120) NOT NULL,
  email varchar(254) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS customers_email_lower_uq ON customers (lower(email));

CREATE TABLE IF NOT EXISTS licenses (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  license_ref text NOT NULL UNIQUE,
  product_id uuid NOT NULL REFERENCES products(id),
  customer_id uuid REFERENCES customers(id) ON DELETE SET NULL,
  order_ref varchar(120),
  key_salt text NOT NULL,
  key_hash text NOT NULL,
  key_lookup text NOT NULL UNIQUE,
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked','expired')),
  expires_at timestamptz,
  max_devices integer NOT NULL DEFAULT 1 CHECK (max_devices BETWEEN 1 AND 20),
  created_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz
);
CREATE INDEX IF NOT EXISTS licenses_status_expiry_idx ON licenses(status, expires_at);

CREATE TABLE IF NOT EXISTS activations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  license_id uuid NOT NULL REFERENCES licenses(id),
  device_binding text NOT NULL,
  app_version text NOT NULL DEFAULT '',
  activated_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  deactivated_at timestamptz,
  UNIQUE (license_id, device_binding)
);
CREATE INDEX IF NOT EXISTS activations_active_license_idx
  ON activations(license_id) WHERE deactivated_at IS NULL;

CREATE TABLE IF NOT EXISTS license_events (
  id bigserial PRIMARY KEY,
  license_id uuid REFERENCES licenses(id),
  event_type text NOT NULL,
  actor text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS license_events_license_time_idx ON license_events(license_id, created_at DESC);

-- Shared API rate-limit windows. Expired buckets are pruned periodically by the API.
CREATE TABLE IF NOT EXISTS api_rate_limit_buckets (
  bucket_key text PRIMARY KEY,
  window_started_at timestamptz NOT NULL,
  request_count integer NOT NULL CHECK (request_count > 0)
);
CREATE INDEX IF NOT EXISTS api_rate_limit_buckets_window_idx
  ON api_rate_limit_buckets(window_started_at);


-- Customer support inbox: public submissions are rate-limited and ticket access
-- is protected by a one-time random customer token (only its hash is stored).
CREATE TABLE IF NOT EXISTS support_tickets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_ref text NOT NULL UNIQUE,
  access_token_hash text NOT NULL,
  email text NOT NULL DEFAULT '',
  category text NOT NULL CHECK (category IN ('question','bug','suggestion','billing','license')),
  subject text NOT NULL,
  message text NOT NULL,
  status text NOT NULL DEFAULT 'open' CHECK (status IN ('open','in_progress','answered','closed')),
  admin_reply text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  replied_at timestamptz
);
CREATE INDEX IF NOT EXISTS support_tickets_status_time_idx
  ON support_tickets(status, created_at DESC);

-- Persist a single Guardian administrator bearer-token verifier.
-- The random token itself is never stored in PostgreSQL.
CREATE TABLE IF NOT EXISTS admin_credentials (
  singleton_id smallint PRIMARY KEY DEFAULT 1 CHECK (singleton_id = 1),
  token_hash char(64) NOT NULL CHECK (token_hash ~ '^[a-f0-9]{64}$'),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- Guardian Android and Windows product update metadata.
CREATE TABLE IF NOT EXISTS guardian_updates (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  platform text NOT NULL CHECK (platform IN ('android','windows')),
  channel text NOT NULL CHECK (channel IN ('stable','beta')),
  version text NOT NULL,
  download_url text NOT NULL CHECK (download_url LIKE 'https://%'),
  sha256 char(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
  min_supported_version text,
  mandatory boolean NOT NULL DEFAULT false,
  release_notes text NOT NULL DEFAULT '',
  active boolean NOT NULL DEFAULT true,
  published_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(platform,channel,version)
);
CREATE INDEX IF NOT EXISTS guardian_updates_latest_idx
  ON guardian_updates(platform,channel,published_at DESC) WHERE active=true;
