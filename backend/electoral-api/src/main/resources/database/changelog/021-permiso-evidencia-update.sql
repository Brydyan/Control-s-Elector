--liquibase formatted sql

-- ============================================================================
-- 021 — Permiso evidencia:update (T-BE-16)
--
-- Cubre la operación PATCH /api/v1/evidencias/{id}/observaciones.
-- Asignado a OBSERVER, COORDINATOR y MASTER. NO a ADMIN — ADMIN solo lee.
-- ============================================================================


-- changeset electoral:021-01-permiso-evidencia-update
INSERT INTO permiso (public_id, recurso, accion) VALUES
    (gen_random_uuid(), 'evidencia', 'update')
ON CONFLICT (recurso, accion) DO NOTHING;
-- rollback DELETE FROM permiso WHERE recurso = 'evidencia' AND accion = 'update';


-- changeset electoral:021-02-rol-permiso-evidencia-update
-- Asigna evidencia:update a OBSERVER, COORDINATOR y MASTER (no a ADMIN).
INSERT INTO rol_permiso (public_id, rol_id, permiso_id)
SELECT gen_random_uuid(), r.id, p.id
FROM rol r, permiso p
WHERE p.recurso = 'evidencia' AND p.accion = 'update'
  AND r.nombre IN ('OBSERVER', 'COORDINATOR', 'MASTER')
  AND NOT EXISTS (
      SELECT 1 FROM rol_permiso rp
      WHERE rp.rol_id = r.id AND rp.permiso_id = p.id
  );
-- rollback DELETE FROM rol_permiso
--   WHERE permiso_id = (SELECT id FROM permiso WHERE recurso='evidencia' AND accion='update');
