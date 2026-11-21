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
  expires_at timestamptz NOT NULL,
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


-- Google Play purchase ownership and RTDN event ledger (migration 006).
-- Google Play subscription ownership and idempotent notification ledger.
-- Raw purchase tokens and Pub/Sub payloads are never persisted.
CREATE TABLE IF NOT EXISTS google_play_purchases (
  purchase_token_hash char(64) PRIMARY KEY CHECK (purchase_token_hash ~ '^[0-9a-f]{64}$'),
  product_id uuid NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
  license_id uuid NOT NULL UNIQUE REFERENCES licenses(id) ON DELETE RESTRICT,
  product_sku varchar(120) NOT NULL,
  subscription_state varchar(64) NOT NULL,
  expires_at timestamptz NOT NULL,
  latest_order_id varchar(120),
  acknowledgement_state varchar(64) NOT NULL DEFAULT 'ACKNOWLEDGEMENT_STATE_UNSPECIFIED',
  last_verified_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS google_play_purchases_expiry_idx
  ON google_play_purchases(expires_at);

CREATE TABLE IF NOT EXISTS google_play_rtdn_events (
  message_id varchar(200) PRIMARY KEY,
  event_type varchar(80) NOT NULL,
  purchase_token_hash char(64) CHECK (purchase_token_hash IS NULL OR purchase_token_hash ~ '^[0-9a-f]{64}$'),
  payload_hash char(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
  received_at timestamptz NOT NULL DEFAULT now(),
  processed_at timestamptz
);
CREATE INDEX IF NOT EXISTS google_play_rtdn_events_received_idx
  ON google_play_rtdn_events(received_at DESC);
