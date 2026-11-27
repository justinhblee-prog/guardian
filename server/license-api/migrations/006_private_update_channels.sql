CREATE TABLE IF NOT EXISTS guardian_update_manifests (
  id BIGSERIAL PRIMARY KEY,
  channel TEXT NOT NULL CHECK (channel IN ('stable', 'beta', 'dev')),
  version_code INTEGER NOT NULL CHECK (version_code > 0),
  version_name TEXT NOT NULL CHECK (length(version_name) BETWEEN 1 AND 64),
  object_key TEXT NOT NULL UNIQUE,
  sha256 TEXT NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
  signer_sha256 TEXT NOT NULL CHECK (signer_sha256 ~ '^[a-f0-9]{64}$'),
  size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 104857600),
  min_supported_version_code INTEGER NOT NULL DEFAULT 1 CHECK (min_supported_version_code > 0),
  release_notes TEXT NOT NULL DEFAULT '',
  published BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  UNIQUE (channel, version_code)
);
CREATE INDEX IF NOT EXISTS guardian_update_manifests_published_idx
  ON guardian_update_manifests(channel, version_code DESC)
  WHERE published = TRUE;
