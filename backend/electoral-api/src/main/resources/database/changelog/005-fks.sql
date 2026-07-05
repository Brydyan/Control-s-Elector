--liquibase formatted sql

-- ============================================================================
-- 005 - Tabla rol + migracion de datos.
--
-- Normaliza el rol de VARCHAR a entidad propia con FK.
--   Antes: usuario.rol     VARCHAR(32)  (string suelto)
--          rol_permiso.rol VARCHAR(32)  (string suelto)
--   Despues: TABLA rol (id, nombre, public_id) con FKs desde usuario y
--            rol_permiso. Refuerza integridad referencial a nivel DB.
--
-- Las FKs de 001-initial-schema (evidencia->usuario, sesion->usuario,
-- menu_permiso->menu, menu_permiso->permiso, rol_permiso->permiso) fueron
-- creadas por una version anterior de este archivo y ya existen en la DB.
-- menu.parent_id tiene FK inline desde 003-01.
-- jrv_id y cargo_id en evidencia quedan sin FK (tablas JRV/Cargo no existen).
-- ============================================================================


-- ============================================================================
-- PARTE A: Tabla rol
-- ============================================================================

-- changeset electoral:005-01-create-rol
CREATE TABLE rol (
    id         BIGSERIAL     PRIMARY KEY,
    nombre     VARCHAR(32)   NOT NULL,
    public_id  UUID          NOT NULL DEFAULT gen_random_uuid(),
    creado_en  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rol_nombre UNIQUE (nombre)
);
-- rollback DROP TABLE rol;


-- changeset electoral:005-02-seed-rol
INSERT INTO rol (nombre) VALUES
    ('MASTER'),
    ('COORDINATOR'),
    ('ADMIN'),
    ('OBSERVER')
ON CONFLICT (nombre) DO NOTHING;
-- rollback DELETE FROM rol WHERE nombre IN ('MASTER','COORDINATOR','ADMIN','OBSERVER');


-- changeset electoral:005-03-add-rol-id-to-usuario
ALTER TABLE usuario ADD COLUMN rol_id BIGINT;
-- rollback ALTER TABLE usuario DROP COLUMN rol_id;


-- changeset electoral:005-04-migrate-usuario-rol
UPDATE usuario u
    SET rol_id = (SELECT id FROM rol WHERE nombre = u.rol);
-- rollback UPDATE usuario SET rol_id = NULL;


-- changeset electoral:005-05-finalize-usuario-rol
ALTER TABLE usuario
    ALTER COLUMN rol_id SET NOT NULL,
    ADD CONSTRAINT fk_usuario_rol FOREIGN KEY (rol_id) REFERENCES rol (id);
CREATE INDEX ix_usuario_rol_id ON usuario (rol_id);
-- rollback ALTER TABLE usuario DROP CONSTRAINT fk_usuario_rol;
--   DROP INDEX ix_usuario_rol_id;


-- changeset electoral:005-06-drop-usuario-rol-varchar
ALTER TABLE usuario DROP COLUMN rol;
DROP INDEX IF EXISTS ix_usuario_rol;
-- rollback ALTER TABLE usuario ADD COLUMN rol VARCHAR(32);
--        CREATE INDEX ix_usuario_rol ON usuario (rol);


-- changeset electoral:005-07-add-rol-id-to-rol-permiso
ALTER TABLE rol_permiso ADD COLUMN rol_id BIGINT;
-- rollback ALTER TABLE rol_permiso DROP COLUMN rol_id;


-- changeset electoral:005-08-migrate-rol-permiso-rol
UPDATE rol_permiso rp
    SET rol_id = (SELECT id FROM rol WHERE nombre = rp.rol);
-- rollback UPDATE rol_permiso SET rol_id = NULL;


-- changeset electoral:005-09-finalize-rol-permiso-rol
ALTER TABLE rol_permiso
    ALTER COLUMN rol_id SET NOT NULL,
    ADD CONSTRAINT fk_rol_permiso_rol FOREIGN KEY (rol_id) REFERENCES rol (id);
CREATE INDEX ix_rol_permiso_rol_id ON rol_permiso (rol_id);
-- rollback ALTER TABLE rol_permiso DROP CONSTRAINT fk_rol_permiso_rol;
--   DROP INDEX ix_rol_permiso_rol_id;


-- changeset electoral:005-10-drop-rol-permiso-rol-varchar
ALTER TABLE rol_permiso DROP COLUMN rol;
DROP INDEX IF EXISTS ix_rol_permiso_rol;
-- rollback ALTER TABLE rol_permiso ADD COLUMN rol VARCHAR(32);
--        CREATE INDEX ix_rol_permiso_rol ON rol_permiso (rol);


-- ============================================================================
-- NOTA: Las FKs de 001-initial-schema (evidencia→usuario, sesion→usuario,
-- rol_permiso→permiso, menu_permiso→menu, menu_permiso→permiso) fueron
-- creadas por una version anterior de este archivo. Ya estan en la DB:
--   SELECT conname FROM pg_constraint WHERE contype='f' AND conrelid::regclass::text NOT IN ('databasechangelog','databasechangeloglock');
-- ============================================================================
