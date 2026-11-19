-- Customer directory for Guardian License Admin. Safe for existing installations.
CREATE TABLE IF NOT EXISTS customers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name varchar(120) NOT NULL,
  email varchar(254) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS customers_email_lower_uq ON customers (lower(email));
ALTER TABLE licenses ADD COLUMN IF NOT EXISTS customer_id uuid REFERENCES customers(id) ON DELETE SET NULL;
ALTER TABLE licenses ADD COLUMN IF NOT EXISTS order_ref varchar(120);
CREATE INDEX IF NOT EXISTS licenses_customer_created_idx ON licenses(customer_id, created_at DESC);
