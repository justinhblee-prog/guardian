-- Product release metadata for Guardian Android and Windows update checks.
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
