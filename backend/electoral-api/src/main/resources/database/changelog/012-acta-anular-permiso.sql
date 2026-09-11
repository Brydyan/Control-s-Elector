--liquibase formatted sql

-- changeset electoral:012-01-create-acta-anular-permiso
INSERT INTO permiso (public_id, recurso, accion)
VALUES (gen_random_uuid(), 'acta', 'anular')
ON CONFLICT (recurso, accion) DO NOTHING;

-- changeset electoral:012-02-assign-acta-anular-to-master
INSERT INTO rol_permiso (public_id, rol_id, permiso_id)
SELECT gen_random_uuid(), r.id, p.id
FROM rol r, permiso p
WHERE r.nombre = 'MASTER'
  AND p.recurso = 'acta' AND p.accion = 'anular'
  AND NOT EXISTS (
      SELECT 1 FROM rol_permiso rp
      WHERE rp.rol_id = r.id AND rp.permiso_id = p.id
  );
