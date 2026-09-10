--liquibase formatted sql

-- changeset electoral:005-03-add-rol-id-columns
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS rol_id BIGINT;
ALTER TABLE rol_permiso ADD COLUMN IF NOT EXISTS rol_id BIGINT;
-- rollback ALTER TABLE usuario DROP COLUMN IF EXISTS rol_id; ALTER TABLE rol_permiso DROP COLUMN IF EXISTS rol_id;

-- changeset electoral:005-04-migrate-roles
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='usuario' AND column_name='rol') THEN
        UPDATE usuario u SET rol_id = r.id FROM rol r WHERE r.nombre = u.rol AND u.rol_id IS NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rol_permiso' AND column_name='rol') THEN
        UPDATE rol_permiso rp SET rol_id = r.id FROM rol r WHERE r.nombre = rp.rol AND rp.rol_id IS NULL;
    END IF;
END $$;
-- rollback UPDATE usuario SET rol_id = NULL; UPDATE rol_permiso SET rol_id = NULL;

-- changeset electoral:005-05-finalize-role-fks
ALTER TABLE usuario ALTER COLUMN rol_id SET NOT NULL;
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_rol FOREIGN KEY (rol_id) REFERENCES rol(id);
CREATE INDEX IF NOT EXISTS ix_usuario_rol_id ON usuario(rol_id);
ALTER TABLE rol_permiso ALTER COLUMN rol_id SET NOT NULL;
ALTER TABLE rol_permiso ADD CONSTRAINT fk_rol_permiso_rol FOREIGN KEY (rol_id) REFERENCES rol(id);
CREATE INDEX IF NOT EXISTS ix_rol_permiso_rol_id ON rol_permiso(rol_id);
-- rollback ALTER TABLE usuario DROP CONSTRAINT IF EXISTS fk_usuario_rol; ALTER TABLE rol_permiso DROP CONSTRAINT IF EXISTS fk_rol_permiso_rol;

-- changeset electoral:005-06-drop-legacy-role-columns
ALTER TABLE usuario DROP COLUMN IF EXISTS rol;
ALTER TABLE rol_permiso DROP COLUMN IF EXISTS rol;
DROP INDEX IF EXISTS ix_usuario_rol;
DROP INDEX IF EXISTS ix_rol_permiso_rol;
-- rollback -- legacy columns intentionally not restored

-- changeset electoral:005-07-missing-foreign-keys
ALTER TABLE rol_permiso ADD CONSTRAINT fk_rol_permiso_permiso FOREIGN KEY (permiso_id) REFERENCES permiso(id);
ALTER TABLE menu_permiso ADD CONSTRAINT fk_menu_permiso_menu FOREIGN KEY (menu_id) REFERENCES menu(id);
ALTER TABLE menu_permiso ADD CONSTRAINT fk_menu_permiso_permiso FOREIGN KEY (permiso_id) REFERENCES permiso(id);
ALTER TABLE sesion ADD CONSTRAINT fk_sesion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id);
ALTER TABLE evidencia ADD CONSTRAINT fk_evidencia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id);
-- rollback ALTER TABLE rol_permiso DROP CONSTRAINT IF EXISTS fk_rol_permiso_permiso;
