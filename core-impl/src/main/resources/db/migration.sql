-- ============================================================
-- Migration des schémas existants
-- Exécuté après schema.sql; ignore les colonnes déjà présentes.
-- ============================================================

-- v1: ajout des colonnes sync sur incidents
ALTER TABLE incidents ADD COLUMN base_updated_at TEXT;
ALTER TABLE incidents ADD COLUMN synced_at INTEGER;
ALTER TABLE incidents ADD COLUMN is_dirty INTEGER NOT NULL DEFAULT 0;
ALTER TABLE local_accounts ADD COLUMN refresh_token TEXT;

-- v2: mapping quartier + whitelist
CREATE TABLE IF NOT EXISTS mapping_neighbourhood_id (
    neighbourhood_id   TEXT NOT NULL,
    neighbourhood_name TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS sync_whitelist (
    entity_type TEXT NOT NULL,
    field_name  TEXT NOT NULL,
    PRIMARY KEY (entity_type, field_name)
);

-- v3: outbox pattern — sync_changelog remplace is_dirty/base_updated_at
ALTER TABLE sync_state ADD COLUMN latest_sync_cursor TEXT;
ALTER TABLE sync_state ADD COLUMN resume_cursor TEXT;
ALTER TABLE sync_changelog ADD COLUMN base_updated_at TEXT;
-- v3: sync_changelog n'a plus besoin de synced_at (DELETE on push success)
-- (SQLite ne supporte pas DROP COLUMN avant 3.35, on ignore les anciennes colonnes)
