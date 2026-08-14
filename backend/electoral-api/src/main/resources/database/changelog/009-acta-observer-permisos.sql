--liquibase formatted sql

-- ============================================================================
-- 009 - Permiso 'acta:upload' para el flujo de registro/correccion del
-- OBSERVER (T-BE-11 - ObserverController).
--
-- El seed 002-rbac-seed.sql solo sembro 'acta:read', 'acta:aprobar' y
-- 'acta:rechazar'. Faltaba el permiso de escritura que usa
-- ObserverController en POST /api/v1/observer/actas y
-- PUT /api/v1/observer/actas/{id}/corregir.
-- ============================================================================


-- changeset electoral:009-01-permiso-acta-upload
INSERT INTO permiso (public_id, recurso, accion) VALUES
    (gen_random_uuid(), 'acta', 'upload')
ON CONFLICT (recurso, accion) DO NOTHING;
-- rollback DELETE FROM permiso WHERE recurso = 'acta' AND accion = 'upload';


-- changeset electoral:009-02-rol-permiso-acta-upload
-- Solo OBSERVER registra/corrige actas.
INSERT INTO rol_permiso (public_id, rol_id, permiso_id)
SELECT gen_random_uuid(), r.id, p.id
FROM rol r, permiso p
WHERE r.nombre = 'OBSERVER'
  AND p.recurso = 'acta' AND p.accion = 'upload'
  AND NOT EXISTS (
      SELECT 1 FROM rol_permiso rp WHERE rp.rol_id = r.id AND rp.permiso_id = p.id
  );
-- rollback DELETE FROM rol_permiso WHERE rol_id = (SELECT id FROM rol WHERE nombre = 'OBSERVER')
--   AND permiso_id = (SELECT id FROM permiso WHERE recurso = 'acta' AND accion = 'upload');
