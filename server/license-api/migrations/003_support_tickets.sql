-- Guardian customer support inbox
-- Safe to apply repeatedly on existing license-api databases.
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
