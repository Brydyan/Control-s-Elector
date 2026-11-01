--liquibase formatted sql

-- ============================================================================
-- 023 — Incidencias operativas de JRV (T-BE-19)
--
-- Tabla para reportar contingencias operativas donde no es posible (o aún no
-- corresponde) generar un acta: JRV no instalada, urna violentada, disturbios,
-- corte de energía, etc. Aislada de `acta` (per design.md D1) porque no
-- representa votos; es un evento operativo.
-- ============================================================================


-- changeset electoral:023-01-create-incidencia-jrv
CREATE TABLE incidencia_jrv (
    id              BIGSERIAL    PRIMARY KEY,
    jrv_id          BIGINT       NOT NULL REFERENCES jrv(id),
    tipo            VARCHAR(50)  NOT NULL,
    descripcion     TEXT         NOT NULL,
    evidencia_url   VARCHAR(500),
    reportada_por_id BIGINT      NOT NULL REFERENCES usuario(id),
    fecha_registro  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_incidencia_jrv_id    ON incidencia_jrv (jrv_id);
CREATE INDEX idx_incidencia_fecha    ON incidencia_jrv (fecha_registro DESC);
CREATE INDEX idx_incidencia_tipo     ON incidencia_jrv (tipo);
-- rollback DROP TABLE incidencia_jrv;
