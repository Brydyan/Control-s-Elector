--liquibase formatted sql

-- ============================================================================
-- 014 - Seed completo de menús dinámicos alineados a README.md y permisos RBAC
-- ============================================================================

-- changeset electoral:014-01-actualizar-menus-existentes
-- Actualizar rutas y labels canónicos
UPDATE menu SET label = 'Dashboard', icono = 'home', ruta = '/dashboard', orden = 1 WHERE codigo = 'dashboard';
UPDATE menu SET label = 'Actas', icono = 'document', ruta = '/actas', orden = 2 WHERE codigo = 'actas';
UPDATE menu SET label = 'Evidencias', icono = 'image', ruta = '/evidencias', orden = 5 WHERE codigo = 'evidencias';
UPDATE menu SET label = 'Administración', icono = 'settings', ruta = '/admin', orden = 6 WHERE codigo = 'admin';

-- Reorganizar submenús bajo Operación Electoral (OBSERVER)
-- actas.registrar y evidencias.subir se mueven bajo "observer"
UPDATE menu SET label = 'Registrar Acta', icono = 'add-circle', ruta = '/observer/registrar-acta',
  parent_id = (SELECT id FROM menu WHERE codigo = 'observer' AND borrado_en IS NULL), orden = 2
  WHERE codigo = 'actas.registrar';

UPDATE menu SET label = 'Subir Evidencia', icono = 'cloud-upload', ruta = '/observer/evidencia',
  parent_id = (SELECT id FROM menu WHERE codigo = 'observer' AND borrado_en IS NULL), orden = 3
  WHERE codigo = 'evidencias.subir';

-- Reordenar Aprobar/Rechazar bajo Actas (COORDINATOR/MASTER)
UPDATE menu SET label = 'Aprobar / Rechazar', icono = 'checkmark', ruta = '/coordinator/actas', orden = 1
  WHERE codigo = 'actas.aprobar';

-- Actualizar orden de Operación Electoral a 3
UPDATE menu SET orden = 3 WHERE codigo = 'observer' AND borrado_en IS NULL;

-- changeset electoral:014-02-insertar-menus-faltantes
INSERT INTO menu (public_id, codigo, label, icono, ruta, orden, parent_id) VALUES
    -- Menú Operación Electoral para OBSERVER
    (gen_random_uuid(), 'observer', 'Operación Electoral', 'ballot', '/observer/mis-jrvs', 4, NULL)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO menu (public_id, codigo, label, icono, ruta, orden, parent_id) VALUES
    (gen_random_uuid(), 'observer.jrvs', 'Mis JRVs', 'checkbox', '/observer/mis-jrvs', 1,
        (SELECT id FROM menu WHERE codigo = 'observer' AND borrado_en IS NULL))
ON CONFLICT (codigo) DO NOTHING;

-- Submenús de Configuración del Sistema (MASTER)
INSERT INTO menu (public_id, codigo, label, icono, ruta, orden, parent_id) VALUES
    (gen_random_uuid(), 'admin.cargos', 'Cargos Electorales', 'ribbon', '/master/cargos', 3,
        (SELECT id FROM menu WHERE codigo = 'admin' AND borrado_en IS NULL)),
    (gen_random_uuid(), 'admin.partidos', 'Partidos Políticos', 'flag', '/master/partidos', 4,
        (SELECT id FROM menu WHERE codigo = 'admin' AND borrado_en IS NULL)),
    (gen_random_uuid(), 'admin.candidatos', 'Candidatos', 'person', '/master/candidatos', 5,
        (SELECT id FROM menu WHERE codigo = 'admin' AND borrado_en IS NULL))
ON CONFLICT (codigo) DO NOTHING;

-- changeset electoral:014-03-asociar-menu-permisos
-- Asociar observer.jrvs con acta:read o acta:upload
INSERT INTO menu_permiso (public_id, menu_id, permiso_id)
SELECT gen_random_uuid(), m.id, p.id
FROM menu m
JOIN permiso p ON (
    (m.codigo = 'observer.jrvs' AND p.recurso = 'acta' AND p.accion = 'upload') OR
    (m.codigo = 'admin.cargos' AND p.recurso = 'configuracion' AND p.accion = 'write') OR
    (m.codigo = 'admin.partidos' AND p.recurso = 'configuracion' AND p.accion = 'write') OR
    (m.codigo = 'admin.candidatos' AND p.recurso = 'configuracion' AND p.accion = 'write')
)
WHERE m.borrado_en IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM menu_permiso mp
      WHERE mp.menu_id = m.id AND mp.permiso_id = p.id
  );
