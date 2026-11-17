-- changeset electoral:035-01-permiso-configuracion-read
-- La lectura de la configuracion electoral (incluida la geografia) queda con permiso
-- propio: hasta aqui el unico permiso de configuracion era 'configuracion:write' y la
-- lectura de cargos/partidos/candidatos quedaba abierta a cualquier autenticado.
-- ADMIN tiene ahora 'configuracion:read' (ver perfil de roles: ADMIN es solo lectura).
INSERT INTO permiso (public_id, recurso, accion) VALUES
    (gen_random_uuid(), 'configuracion', 'read')
ON CONFLICT (recurso, accion) DO NOTHING;
-- rollback DELETE FROM permiso WHERE recurso = 'configuracion' AND accion = 'read';

-- changeset electoral:035-02-rol-permiso-configuracion-read
-- MASTER y ADMIN pueden leer la configuracion electoral.
INSERT INTO rol_permiso (public_id, rol_id, permiso_id)
SELECT gen_random_uuid(), r.id, p.id
FROM rol r, permiso p
WHERE r.nombre IN ('MASTER', 'ADMIN')
  AND p.recurso = 'configuracion' AND p.accion = 'read'
  AND NOT EXISTS (
      SELECT 1 FROM rol_permiso rp
      WHERE rp.rol_id = r.id AND rp.permiso_id = p.id
  );
-- rollback DELETE FROM rol_permiso rp USING rol r, permiso p WHERE rp.rol_id = r.id AND rp.permiso_id = p.id AND r.nombre IN ('MASTER','ADMIN') AND p.recurso = 'configuracion' AND p.accion = 'read';

-- changeset electoral:035-03-menu-config-geografica
-- Submenu de Configuracion del Sistema (MASTER) para la geografia jerarquica.
INSERT INTO menu (public_id, codigo, label, icono, ruta, orden, parent_id) VALUES
    (gen_random_uuid(), 'admin.geografia', 'Configuración Geográfica', 'map',
        '/master/configuracion-geografica', 6,
        (SELECT id FROM menu WHERE codigo = 'admin' AND borrado_en IS NULL))
ON CONFLICT (codigo) DO NOTHING;
-- rollback DELETE FROM menu WHERE codigo = 'admin.geografia';

-- changeset electoral:035-04-menu-permiso-config-geografica
-- El menu de geografia exige 'configuracion:write': solo MASTER lo ve.
INSERT INTO menu_permiso (public_id, menu_id, permiso_id)
SELECT gen_random_uuid(), m.id, p.id
FROM menu m
JOIN permiso p ON (
    (m.codigo = 'admin.geografia' AND p.recurso = 'configuracion' AND p.accion = 'write')
)
WHERE m.borrado_en IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM menu_permiso mp
      WHERE mp.menu_id = m.id AND mp.permiso_id = p.id
  );
-- rollback DELETE FROM menu_permiso mp USING menu m WHERE mp.menu_id = m.id AND m.codigo = 'admin.geografia';
