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
