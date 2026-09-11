--liquibase formatted sql

-- ============================================================================
-- 013 - Cierra T-BE-14 (vincula evidencia con acta) y T-BE-16 (observaciones).
--
-- Cambios:
--   1. evidencia.acta_id → FK NOT NULL hacia acta (T-BE-14). El campo fue
--      creado nullable en 010; ahora se promueve a obligatorio porque cada
--      evidencia pertenece a una sola acta. La subida exige acta_id en el
--      EvidenciaService (validación a nivel service además del constraint).
--   2. acta_detalle.observaciones TEXT — T-BE-16. COORDINATOR/MASTER pueden
--      dejar notas sobre cada candidato del acta.
-- ============================================================================


-- changeset electoral:013-01-evidencia-acta-fk
ALTER TABLE evidencia ADD CONSTRAINT fk_evidencia_acta
    FOREIGN KEY (acta_id) REFERENCES acta (id) ON DELETE CASCADE;
-- rollback ALTER TABLE evidencia DROP CONSTRAINT fk_evidencia_acta;


-- changeset electoral:013-02-evidencia-acta-not-null
-- Backfill de filas pre-existentes apuntando al acta activa más reciente
-- para (jrv_id, cargo_id). En una BD limpia sin datos, esto no afecta nada.
UPDATE evidencia e
SET acta_id = (
    SELECT a.id FROM acta a
    WHERE a.jrv_id = e.jrv_id
      AND a.cargo_id = e.cargo_id
      AND a.estado != 'ANULADA'
    ORDER BY a.fecha_registro DESC
    LIMIT 1
)
WHERE acta_id IS NULL;
-- rollback -- no rollback del backfill porque la columna sigue aceptando NULL.


-- changeset electoral:013-03-evidencia-acta-not-null-constraint
ALTER TABLE evidencia ALTER COLUMN acta_id SET NOT NULL;
-- rollback ALTER TABLE evidencia ALTER COLUMN acta_id DROP NOT NULL;


-- changeset electoral:013-04-acta-detalle-observaciones
ALTER TABLE acta_detalle ADD COLUMN observaciones TEXT;
-- rollback ALTER TABLE acta_detalle DROP COLUMN IF EXISTS observaciones;