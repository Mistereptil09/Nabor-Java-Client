-- Migration: add sync tracking columns to incidents (v1 → v2)
-- Safe to run multiple times — errors on duplicate columns are suppressed.
ALTER TABLE incidents ADD COLUMN base_updated_at TEXT;
ALTER TABLE incidents ADD COLUMN synced_at INTEGER;
ALTER TABLE incidents ADD COLUMN is_dirty INTEGER NOT NULL DEFAULT 0;
ALTER TABLE local_accounts ADD COLUMN refresh_token TEXT;
