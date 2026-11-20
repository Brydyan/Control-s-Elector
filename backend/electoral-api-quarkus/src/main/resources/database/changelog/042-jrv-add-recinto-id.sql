-- [2026-11-19] Modify jrv table: add recinto_id column
-- Purpose: Link JRV to its parent Recinto
-- Note: Initially nullable for backward compatibility; will be migrated in next changeset

ALTER TABLE jrv ADD COLUMN recinto_id BIGINT REFERENCES recinto(id);
CREATE INDEX idx_jrv_recinto_id ON jrv(recinto_id);
