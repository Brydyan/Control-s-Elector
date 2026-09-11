--liquibase formatted sql

-- changeset electoral:010-01-tablas-jpa
CREATE TABLE acta (
 id BIGSERIAL PRIMARY KEY, jrv_id BIGINT NOT NULL REFERENCES jrv(id), cargo_id BIGINT NOT NULL REFERENCES cargo(id),
 registrada_por BIGINT NOT NULL REFERENCES usuario(id), estado VARCHAR(20) NOT NULL, storage_url VARCHAR(512),
 total_sufragantes INTEGER NOT NULL CHECK (total_sufragantes >= 0), votos_blancos INTEGER NOT NULL CHECK (votos_blancos >= 0),
 votos_nulos INTEGER NOT NULL CHECK (votos_nulos >= 0), fecha_registro TIMESTAMPTZ NOT NULL DEFAULT NOW(), motivo_anulacion VARCHAR(500),
 public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(), version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE acta_detalle (
 id BIGSERIAL PRIMARY KEY, acta_id BIGINT NOT NULL REFERENCES acta(id) ON DELETE CASCADE, candidato_id BIGINT NOT NULL REFERENCES candidato(id),
 votos INTEGER NOT NULL CHECK (votos >= 0), version BIGINT NOT NULL DEFAULT 0, UNIQUE(acta_id, candidato_id)
);
CREATE TABLE observer_jrv_asignacion (
 id BIGSERIAL PRIMARY KEY, usuario_id BIGINT NOT NULL REFERENCES usuario(id), jrv_id BIGINT NOT NULL REFERENCES jrv(id),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(), UNIQUE(usuario_id, jrv_id)
);
CREATE TABLE audit_log (
 id BIGSERIAL PRIMARY KEY, entidad VARCHAR(64) NOT NULL, entidad_id BIGINT NOT NULL, accion VARCHAR(32) NOT NULL, detalle JSONB,
 ip_address VARCHAR(64), registrado_por BIGINT REFERENCES usuario(id), timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE notificacion (
 id BIGSERIAL PRIMARY KEY, destinatario_id BIGINT NOT NULL REFERENCES usuario(id), tipo VARCHAR(64) NOT NULL,
 acta_id BIGINT REFERENCES acta(id), mensaje VARCHAR(500) NOT NULL,
 leida BOOLEAN NOT NULL DEFAULT FALSE, creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uk_acta_activa ON acta(jrv_id, cargo_id) WHERE estado IN ('BORRADOR','ENVIADA','EN_DISPUTA','CORREGIDA');
-- rollback DROP TABLE notificacion; DROP TABLE audit_log; DROP TABLE observer_jrv_asignacion; DROP TABLE acta_detalle; DROP TABLE acta;

-- changeset electoral:010-02-evidencia-acta
ALTER TABLE evidencia ADD COLUMN acta_id BIGINT;
CREATE INDEX ix_evidencia_acta ON evidencia(acta_id);
-- rollback DROP INDEX ix_evidencia_acta; ALTER TABLE evidencia DROP COLUMN acta_id;
