--liquibase formatted sql

-- ============================================================================
-- 004 - UUIDv7 Public IDs
--
-- Agrega la columna public_id a las tablas que no la tenian desde 001/003
-- (evidencia, usuario, menu, menu_permiso), backfilla filas existentes con
-- gen_random_uuid(), y agrega restricciones UNIQUE + NOT NULL.
--
-- Las tablas permiso y rol_permiso YA TIENEN public_id desde 001 (definidas
-- inline en sus CREATE TABLE). Este changelog NO las duplica; quedan cubiertas
-- por 001-02 y 001-03 respectivamente.
--
-- Idempotente: todos los ALTER usan IF NOT EXISTS / DROP IF EXISTS para que
-- re-ejecuciones sobre BD parcialmente migrada no fallen. Patron consistente
-- con 005-fks.sql.
-- ============================================================================


-- changeset electoral:004-01-evidencia-public-id
ALTER TABLE evidencia ADD COLUMN IF NOT EXISTS public_id UUID;
UPDATE evidencia SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE evidencia ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE evidencia DROP CONSTRAINT IF EXISTS uk_evidencia_public_id;
ALTER TABLE evidencia ADD CONSTRAINT uk_evidencia_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX IF NOT EXISTS ix_evidencia_public_id ON evidencia (public_id);
-- rollback ALTER TABLE evidencia DROP CONSTRAINT uk_evidencia_public_id;
-- rollback DROP INDEX ix_evidencia_public_id;
-- rollback ALTER TABLE evidencia DROP COLUMN public_id;


-- changeset electoral:004-02-usuario-public-id
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS public_id UUID;
UPDATE usuario SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE usuario ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS uk_usuario_public_id;
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX IF NOT EXISTS ix_usuario_public_id ON usuario (public_id);
-- rollback ALTER TABLE usuario DROP CONSTRAINT uk_usuario_public_id;
-- rollback DROP INDEX ix_usuario_public_id;
-- rollback ALTER TABLE usuario DROP COLUMN public_id;


-- changeset electoral:004-03-menu-public-id
-- Solo menu y menu_permiso: permiso y rol_permiso ya tienen public_id desde 001.
ALTER TABLE menu ADD COLUMN IF NOT EXISTS public_id UUID;
UPDATE menu SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE menu ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE menu DROP CONSTRAINT IF EXISTS uk_menu_public_id;
ALTER TABLE menu ADD CONSTRAINT uk_menu_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX IF NOT EXISTS ix_menu_public_id ON menu (public_id);

ALTER TABLE menu_permiso ADD COLUMN IF NOT EXISTS public_id UUID;
UPDATE menu_permiso SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE menu_permiso ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE menu_permiso DROP CONSTRAINT IF EXISTS uk_menu_permiso_public_id;
ALTER TABLE menu_permiso ADD CONSTRAINT uk_menu_permiso_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX IF NOT EXISTS ix_menu_permiso_public_id ON menu_permiso (public_id);
-- rollback ALTER TABLE menu DROP CONSTRAINT uk_menu_public_id;
-- rollback DROP INDEX ix_menu_public_id;
-- rollback ALTER TABLE menu DROP COLUMN public_id;
-- rollback ALTER TABLE menu_permiso DROP CONSTRAINT uk_menu_permiso_public_id;
-- rollback DROP INDEX ix_menu_permiso_public_id;
-- rollback ALTER TABLE menu_permiso DROP COLUMN public_id;
