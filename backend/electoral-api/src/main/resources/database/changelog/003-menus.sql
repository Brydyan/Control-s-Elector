--liquibase formatted sql

-- ============================================================================
-- 003 - Sistema de menus dinamicos basados en permisos.
--
-- Patron: code-first permissions, data-driven menus. El usuario tiene
-- permisos atomicos (recurso:accion) que ya viven en `permiso`. Cada item
-- de menu declara en `menu_permiso` que permisos requiere para ser visible.
-- El endpoint GET /api/v1/menus/me devuelve el sub-arbol de menus que el
-- usuario autenticado puede ver, incluyendo padres faltantes (clave para
-- que el sidebar muestre la seccion aunque el user no tenga permiso del
-- padre — leccion aprendida de jasrapo-backend, migration
-- 20260517013500_restructure_menus_navigation).
--
-- Decisiones:
--   1. Permisos van en items HOJA (submenu / accion), NO en padres. Los
--      padres se incluyen automaticamente al walk-up. Esto evita el ruido
--      de tener "Ver X / Crear X / Borrar X" como entradas separadas.
--   2. Soft delete (`borrado_en`) en menu y menu_permiso para preservar
--      historial si se elimina una entrada de catalogo.
--   3. `codigo` es la clave de UI estable (ej: "actas.aprobar"). La usa
--      el frontend como identificador de traduccion / i18n, no el `id`
--      que es autonumerico y puede cambiar entre ambientes.
--   4. Seed deterministico con ON CONFLICT DO NOTHING. Si se re-corre el
--      changelog no duplica ni rompe.
-- ============================================================================


-- changeset electoral:003-01-menu
-- Jerarquia: parent_id self-reference. borrado_en = soft delete.
CREATE TABLE menu (
    id          BIGSERIAL     PRIMARY KEY,
    parent_id   BIGINT        REFERENCES menu (id),
    codigo      VARCHAR(64)   NOT NULL,
    label       VARCHAR(128)  NOT NULL,
    icono       VARCHAR(64),
    ruta        VARCHAR(256)  NOT NULL,
    orden       INT           NOT NULL DEFAULT 0,
    activo      BOOLEAN       NOT NULL DEFAULT TRUE,
    borrado_en  TIMESTAMPTZ,
    CONSTRAINT uk_menu_codigo UNIQUE (codigo)
);
CREATE INDEX ix_menu_parent ON menu (parent_id);
CREATE INDEX ix_menu_activo ON menu (activo) WHERE borrado_en IS NULL;
-- rollback DROP TABLE menu;


-- changeset electoral:003-02-menu-permiso
-- N:M entre menu y permiso. Un menu requiere al menos un permiso para
-- ser visible (un menu sin permisos en menu_permiso es publico — no se
-- recomienda pero se permite para el caso "Dashboard" donde todos ven).
CREATE TABLE menu_permiso (
    id          BIGSERIAL   PRIMARY KEY,
    menu_id     BIGINT      NOT NULL,
    permiso_id  BIGINT      NOT NULL,
    borrado_en  TIMESTAMPTZ,
    CONSTRAINT uk_menu_permiso UNIQUE (menu_id, permiso_id)
);
CREATE INDEX ix_menu_permiso_menu ON menu_permiso (menu_id);
CREATE INDEX ix_menu_permiso_permiso ON menu_permiso (permiso_id);
-- rollback DROP TABLE menu_permiso;


-- changeset electoral:003-03-menu-seed
-- Seed de menus del sistema. Orden de insercion:
--   1. Padres nivel 1 (parent_id NULL).
--   2. Hijos nivel 2 referenciando a los padres por codigo.
-- Los permisos en menu_permiso se asignan en el changeset 003-04.
INSERT INTO menu (codigo, label, icono, ruta, orden, parent_id) VALUES
    -- Nivel 1: secciones principales
    ('dashboard',  'Dashboard',    'home',         '/home',        1, NULL),
    ('actas',      'Actas',        'document',     '/actas',       2, NULL),
    ('evidencias', 'Evidencias',   'image',        '/evidencias',  3, NULL),
    ('admin',      'Administracion', 'settings',   '/admin',       4, NULL)
ON CONFLICT (codigo) DO NOTHING;
-- rollback DELETE FROM menu WHERE codigo IN ('dashboard','actas','evidencias','admin');


-- changeset electoral:003-04-menu-hijos-seed
-- Nivel 2: items accion bajo cada padre. Los permisos se asignan en 003-05.
-- Cada hijo referencia al padre por codigo via subquery para no hardcodear IDs.
INSERT INTO menu (codigo, label, icono, ruta, orden, parent_id) VALUES
    -- Hijos de Actas
    ('actas.registrar',  'Registrar acta',          'add-circle',  '/actas/nueva',   1,
        (SELECT id FROM menu WHERE codigo = 'actas' AND borrado_en IS NULL)),
    ('actas.aprobar',    'Aprobar / Rechazar',      'checkmark',   '/actas/aprobar', 2,
        (SELECT id FROM menu WHERE codigo = 'actas' AND borrado_en IS NULL)),
    -- Hijos de Evidencias
    ('evidencias.subir', 'Subir evidencia',         'cloud-upload','/evidencias/subir', 1,
        (SELECT id FROM menu WHERE codigo = 'evidencias' AND borrado_en IS NULL)),
    -- Hijos de Administracion (solo MASTER)
    ('admin.usuarios',   'Gestion de usuarios',     'people',      '/admin/usuarios', 1,
        (SELECT id FROM menu WHERE codigo = 'admin' AND borrado_en IS NULL)),
    ('admin.sesiones',   'Sesiones activas',        'pulse',       '/admin/sesiones', 2,
        (SELECT id FROM menu WHERE codigo = 'admin' AND borrado_en IS NULL))
ON CONFLICT (codigo) DO NOTHING;
-- rollback DELETE FROM menu WHERE codigo IN ('actas.registrar','actas.aprobar','evidencias.subir','admin.usuarios','admin.sesiones');


-- changeset electoral:003-05-menu-permiso-seed
-- Asocia cada item-hoja con los permisos que requiere para ser visible.
-- Los padres (dashboard, actas, evidencias, admin) NO tienen menu_permiso:
-- son visibles si al menos un hijo es visible (regla del walk-up en MenuService).
--
-- Matriz:
--   actas.registrar    -> acta:read         (OBSERVER+)
--   actas.aprobar      -> acta:aprobar      (COORDINATOR+)
--   evidencias.subir   -> evidencia:upload  (OBSERVER+)
--   admin.usuarios     -> usuario:create    (solo MASTER)
--   admin.sesiones     -> sesion:list       (solo MASTER)
INSERT INTO menu_permiso (menu_id, permiso_id)
SELECT m.id, p.id
FROM menu m
JOIN permiso p ON (
    (m.codigo = 'actas.registrar'  AND p.recurso = 'acta'      AND p.accion = 'read') OR
    (m.codigo = 'actas.aprobar'    AND p.recurso = 'acta'      AND p.accion = 'aprobar') OR
    (m.codigo = 'evidencias.subir' AND p.recurso = 'evidencia' AND p.accion = 'upload') OR
    (m.codigo = 'admin.usuarios'   AND p.recurso = 'usuario'   AND p.accion = 'create') OR
    (m.codigo = 'admin.sesiones'   AND p.recurso = 'sesion'    AND p.accion = 'list')
)
WHERE m.borrado_en IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM menu_permiso mp
      WHERE mp.menu_id = m.id AND mp.permiso_id = p.id
  );
-- rollback DELETE FROM menu_permiso;