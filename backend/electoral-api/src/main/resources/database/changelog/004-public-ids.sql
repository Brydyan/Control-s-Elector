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

-- changeset electoral:004-02-usuario-public-id
ALTER TABLE usuario ADD COLUMN public_id UUID;
UPDATE usuario SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE usuario ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX ix_usuario_public_id ON usuario (public_id);
-- rollback ALTER TABLE usuario DROP CONSTRAINT uk_usuario_public_id;
-- rollback DROP INDEX ix_usuario_public_id;
-- rollback ALTER TABLE usuario DROP COLUMN public_id;


-- changeset electoral:004-03-rbac-public-id
ALTER TABLE rol_permiso ADD COLUMN public_id UUID;
UPDATE rol_permiso SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE rol_permiso ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE rol_permiso ADD CONSTRAINT uk_rol_permiso_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX ix_rol_permiso_public_id ON rol_permiso (public_id);

ALTER TABLE permiso ADD COLUMN public_id UUID;
UPDATE permiso SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE permiso ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE permiso ADD CONSTRAINT uk_permiso_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX ix_permiso_public_id ON permiso (public_id);

ALTER TABLE menu ADD COLUMN public_id UUID;
UPDATE menu SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE menu ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE menu ADD CONSTRAINT uk_menu_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX ix_menu_public_id ON menu (public_id);

ALTER TABLE menu_permiso ADD COLUMN public_id UUID;
UPDATE menu_permiso SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE menu_permiso ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE menu_permiso ADD CONSTRAINT uk_menu_permiso_public_id UNIQUE (public_id);
CREATE UNIQUE INDEX ix_menu_permiso_public_id ON menu_permiso (public_id);

-- rollback ALTER TABLE rol_permiso DROP CONSTRAINT uk_rol_permiso_public_id;
-- rollback DROP INDEX ix_rol_permiso_public_id;
-- rollback ALTER TABLE rol_permiso DROP COLUMN public_id;
-- rollback ALTER TABLE permiso DROP CONSTRAINT uk_permiso_public_id;
-- rollback DROP INDEX ix_permiso_public_id;
-- rollback ALTER TABLE permiso DROP COLUMN public_id;
-- rollback ALTER TABLE menu DROP CONSTRAINT uk_menu_public_id;
-- rollback DROP INDEX ix_menu_public_id;
-- rollback ALTER TABLE menu DROP COLUMN public_id;
-- rollback ALTER TABLE menu_permiso DROP CONSTRAINT uk_menu_permiso_public_id;
-- rollback DROP INDEX ix_menu_permiso_public_id;
-- rollback ALTER TABLE menu_permiso DROP COLUMN public_id;


