-- One row per logged day. count = 0 rows are kept so clearing a day syncs too.
-- updated_at: when the change was made on the phone (last write wins).
-- synced_at: when the server stored it; phones pull rows with synced_at >= their cursor.
CREATE TABLE IF NOT EXISTS days (
  date TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  synced_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS days_synced_at ON days (synced_at);

-- first_day, and the sign-in throttle counters.
CREATE TABLE IF NOT EXISTS meta (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

-- Signed-in phones. Only a SHA-256 of each token is stored.
CREATE TABLE IF NOT EXISTS tokens (
  hash TEXT PRIMARY KEY,
  device TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
