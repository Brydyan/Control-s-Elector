--liquibase formatted sql

-- ============================================================================
-- 019 — DROP acta.storage_url (B.2 T-BE-14)
--
-- La columna acta.storage_url era el campo legacy del diseño "Opción B"
-- (foto embedida en acta). El sistema adoptó la "Opción A" (tabla EVIDENCIA
-- separada) y la columna quedó muerta: ningún código de producción la escribe.
--
-- Justificación del DROP (no RENAME a storage_key):
--   1. Cero escrituras — siempre NULL en BD.
--   2. La fuente de verdad de la foto del acta es `evidencia.s3_key` (vinculada
--      por acta_id). Una columna desnormalizada suelta de S3 invita a drift.
--   3. Si en el futuro hace falta "foto canónica del acta", lo correcto es
--      `acta.foto_principal_id BIGINT REFERENCES evidencia(id)`, no una clave
--      suelta de S3 que pierde la trazabilidad con la fila de metadata.
--
-- Esta migración es destructiva pero segura: la columna está vacía en todas
-- las BDs (no hay datos que perder).
-- ============================================================================


-- changeset electoral:019-01-drop-acta-storage-url
ALTER TABLE acta DROP COLUMN IF EXISTS storage_url;
-- rollback ALTER TABLE acta ADD COLUMN storage_url VARCHAR(512);
