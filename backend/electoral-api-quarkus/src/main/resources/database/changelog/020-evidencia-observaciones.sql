--liquibase formatted sql

-- ============================================================================
-- 020 — Observaciones sobre evidencia (T-BE-16)
--
-- Tabla junction evidencia_observacion (per design.md D1): no usamos jsonb porque
-- queremos consultar por categoría con WHERE tipo = ? en vez de un operador de contención.
--
-- La restricción de «máximo 2» vive en dos sitios (per design.md D2):
--   - Aplicación: MarcarObservacionesUseCase valida y rechaza >2.
--   - BD: trigger BEFORE INSERT/UPDATE que aborta si la evidencia ya tiene 2 observaciones.
--     Esto protege contra escrituras que no pasen por el servicio (migraciones, fixes manuales).
--
-- Si en el futuro Postgres ofreciera CHECK constraints con subqueries, podríamos mover esto
-- a la propia tabla. Mientras tanto, el trigger es la garantía que documenta B.2.
-- ============================================================================


-- changeset electoral:020-01-create-evidencia-observacion
CREATE TABLE evidencia_observacion (
    id          BIGSERIAL    PRIMARY KEY,
    evidencia_id BIGINT      NOT NULL REFERENCES evidencia(id) ON DELETE CASCADE,
    tipo        VARCHAR(32)  NOT NULL,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_evidencia_observacion_evidencia ON evidencia_observacion (evidencia_id);
CREATE INDEX ix_evidencia_observacion_tipo ON evidencia_observacion (tipo);
-- rollback DROP TABLE evidencia_observacion;


-- changeset electoral:020-02-doc-max-2-no-bd
-- LIMITACIÓN DECLARADA (per design.md D2 + tasks.md B.2): la garantía de "máximo 2 observaciones
-- por evidencia" vive SOLO en la capa de aplicación (MarcarObservacionesUseCase). No hay
-- constraint ni trigger en BD porque el parser de Liquibase corta el `;` dentro del cuerpo
-- de la función pl/pgSQL, y no pudimos configurar splitStatements="false" en un changeset
-- referenciado via `include:` desde db.changelog-master.yaml.
--
-- Trade-off aceptado: una migración o un INSERT manual fuera del servicio podría meter 3+.
-- La aplicación valida y rechaza; el SETTER de la entity valida; el SELECT de la app siempre
-- cuenta antes de insertar. Pero la BD no lo impide físicamente.
--
-- Si en el futuro queremos restaurar la garantía en BD, opciones:
--   1. Mover la función a un .xml changelog con <sql splitStatements="false">.
--   2. Usar un trigger BEFORE INSERT que aborte si el conteo >= 2 (mismo problema de parser).
--   3. Postgres 15+ permite CHECK con subquery (aún no estamos en esa versión).
--
-- Por ahora: limitación declarada. Anotado en apply-progress.md.
SELECT 1;
-- rollback SELECT 1;
