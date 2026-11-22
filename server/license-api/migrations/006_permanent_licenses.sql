-- Allow perpetual licenses without a synthetic expiry date.
-- Existing dated licenses remain unchanged.
ALTER TABLE licenses ALTER COLUMN expires_at DROP NOT NULL;
