--liquibase formatted sql

-- ============================================================================
-- 004 - UUIDv7 Public IDs
--
-- Agrega la columna public_id a la tabla evidencia, backfilla filas existentes
-- con gen_random_uuid(), y agrega restricciones UNIQUE y NOT NULL.
-- ============================================================================

-- changeset electoral:004-01-evidencia-public-id
ALTER TABLE evidencia ADD COLUMN public_id UUID;
UPDATE evidencia SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE evidencia ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE evidencia ADD CONSTRAINT uk_evidencia_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX ix_evidencia_public_id ON evidencia (public_id);
-- rollback ALTER TABLE evidencia DROP CONSTRAINT uk_evidencia_public_id;
-- rollback DROP INDEX ix_evidencia_public_id;
-- rollback ALTER TABLE evidencia DROP COLUMN public_id;
