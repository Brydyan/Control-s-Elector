--liquibase formatted sql

-- ============================================================================
-- 006 - Tablas geograficas: Provincia, Canton, Parroquia, JRV + RefreshToken.
--
-- Refleja las nuevas entidades JPA del package ec.twintailcs.electoral.geografia.domain:
--   Provincia, Canton, Parroquia, Jrv
--
-- Ademas, agrega RefreshToken en auth/domain (antes usaba tabla sesion).
--
-- Tipos: Instant de Java -> TIMESTAMPTZ (UTC). Long autogenerados -> BIGSERIAL.
-- ============================================================================


-- changeset electoral:006-01-create-provincia
CREATE TABLE provincia (
    id       BIGSERIAL     PRIMARY KEY,
    nombre   VARCHAR(100)  NOT NULL,
    codigo   VARCHAR(10)
);
CREATE UNIQUE INDEX ix_provincia_codigo ON provincia (codigo);
-- rollback DROP TABLE provincia;


-- changeset electoral:006-02-create-canton
CREATE TABLE canton (
    id            BIGSERIAL     PRIMARY KEY,
    nombre        VARCHAR(100)  NOT NULL,
    codigo        VARCHAR(10),
    provincia_id  BIGINT        NOT NULL,
    CONSTRAINT fk_canton_provincia FOREIGN KEY (provincia_id) REFERENCES provincia (id)
);
CREATE UNIQUE INDEX ix_canton_codigo ON canton (codigo);
CREATE INDEX ix_canton_provincia ON canton (provincia_id);
-- rollback DROP TABLE canton;


-- changeset electoral:006-03-create-parroquia
CREATE TABLE parroquia (
    id        BIGSERIAL     PRIMARY KEY,
    nombre    VARCHAR(100)  NOT NULL,
    es_urbana BOOLEAN       NOT NULL,
    canton_id BIGINT        NOT NULL,
    CONSTRAINT fk_parroquia_canton FOREIGN KEY (canton_id) REFERENCES canton (id)
);
CREATE INDEX ix_parroquia_canton ON parroquia (canton_id);
-- rollback DROP TABLE parroquia;


-- changeset electoral:006-04-create-jrv
CREATE TABLE jrv (
    id               BIGSERIAL     PRIMARY KEY,
    numero           VARCHAR(20)   NOT NULL,
    recinto          VARCHAR(200)  NOT NULL,
    total_electores  INTEGER       NOT NULL,
    parroquia_id     BIGINT        NOT NULL,
    CONSTRAINT fk_jrv_parroquia FOREIGN KEY (parroquia_id) REFERENCES parroquia (id)
);
CREATE INDEX ix_jrv_parroquia ON jrv (parroquia_id);
-- rollback DROP TABLE jrv;


-- changeset electoral:006-05-create-refresh-token REMOVED 2026-09-13 (PR #94)
-- RefreshToken quedó como código muerto: guardaba el token plano (inseguro) y
-- nunca fue referenciado por la lógica de negocio. El reemplazo correcto es
-- Sesion (BCrypt hash + IP + userAgent + ultimoUsoEn).
