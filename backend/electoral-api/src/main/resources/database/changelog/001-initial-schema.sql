--liquibase formatted sql

-- ============================================================================
-- 001 - Schema inicial de electoral-api (PostgreSQL).
--
-- Refleja las entidades JPA actuales del package ec.twintailcs.electoral:
--   auth/domain:     Usuario, Permiso, RolPermiso, Sesion
--   evidencia/domain: Evidencia
--
-- Equivale a lo que hoy genera Hibernate ddl-auto. Al habilitar Liquibase,
-- este changelog crea el schema y ddl-auto pasa a 'validate'.
--
-- Tipos: los Instant de Java se mapean a TIMESTAMPTZ (UTC). Los boolean a
-- BOOLEAN. Los Long/long autogenerados a BIGSERIAL / BIGINT.
--
-- ============================================================================


-- changeset electoral:001-01-usuario
CREATE TABLE usuario (
    id            BIGSERIAL     PRIMARY KEY,
    email         VARCHAR(254)  NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,
    rol           VARCHAR(32)   NOT NULL,
    habilitado    BOOLEAN       NOT NULL DEFAULT TRUE,
    creado_en     TIMESTAMPTZ   NOT NULL
);
CREATE UNIQUE INDEX ix_usuario_email ON usuario (email);
CREATE INDEX ix_usuario_rol ON usuario (rol);
-- rollback DROP TABLE usuario;


-- changeset electoral:001-02-permiso
-- (recurso, accion) es unico. Ej: evidencia/upload, acta/aprobar.
CREATE TABLE permiso (
    id          BIGSERIAL     PRIMARY KEY,
    recurso     VARCHAR(64)   NOT NULL,
    accion      VARCHAR(64)   NOT NULL,
    descripcion VARCHAR(256),
    CONSTRAINT uk_permiso_recurso_accion UNIQUE (recurso, accion)
);
CREATE INDEX ix_permiso_recurso_accion ON permiso (recurso, accion);
-- rollback DROP TABLE permiso;


-- changeset electoral:001-03-rol_permiso
-- Asignacion rol -> permiso. rol es un string (MASTER/COORDINATOR/ADMIN/OBSERVER).
-- FK a permiso.id documentada; la entidad JPA hoy no la declara. Descomentar
-- para reforzar integridad referencial a nivel DB.
CREATE TABLE rol_permiso (
    id         BIGSERIAL   PRIMARY KEY,
    rol        VARCHAR(32) NOT NULL,
    permiso_id BIGINT      NOT NULL
    -- , CONSTRAINT fk_rol_permiso_permiso FOREIGN KEY (permiso_id) REFERENCES permiso (id)
);
CREATE INDEX ix_rol_permiso_rol ON rol_permiso (rol);
CREATE INDEX ix_rol_permiso_permiso ON rol_permiso (permiso_id);
-- rollback DROP TABLE rol_permiso;


-- changeset electoral:001-04-sesion
-- Sesiones para refresh rotation. refresh_hash es unico (un refresh token vivo).
CREATE TABLE sesion (
    id            BIGSERIAL     PRIMARY KEY,
    usuario_id    BIGINT        NOT NULL,
    sesion_id     VARCHAR(36)   NOT NULL,
    refresh_hash  VARCHAR(100)  NOT NULL,
    revocado      BOOLEAN       NOT NULL DEFAULT FALSE,
    ip_cliente    VARCHAR(64),
    user_agent    VARCHAR(256),
    creado_en     TIMESTAMPTZ   NOT NULL,
    ultimo_uso_en TIMESTAMPTZ   NOT NULL,
    expira_en     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_sesion_sesion_id UNIQUE (sesion_id),
    CONSTRAINT uk_sesion_refresh_hash UNIQUE (refresh_hash)
    -- , CONSTRAINT fk_sesion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);
CREATE INDEX ix_sesion_usuario ON sesion (usuario_id);
CREATE UNIQUE INDEX ix_sesion_refresh ON sesion (refresh_hash);
CREATE INDEX ix_sesion_revocado ON sesion (revocado);
-- rollback DROP TABLE sesion;


-- changeset electoral:001-05-evidencia
-- Fotos de actas subidas a S3. estado es enum STRING (EN_REVISION/APROBADA/RECHAZADA).
-- version = columna de optimistic/pessimistic locking (jrv_id+cargo_id).
-- sha256 sin unique por ahora (ver comentario en la entidad).
CREATE TABLE evidencia (
    id             BIGSERIAL     PRIMARY KEY,
    jrv_id         BIGINT        NOT NULL,
    cargo_id       BIGINT        NOT NULL,
    usuario_id     BIGINT        NOT NULL,
    s3_key         VARCHAR(512)  NOT NULL,
    size_bytes     BIGINT        NOT NULL,
    content_type   VARCHAR(64)   NOT NULL,
    sha256         VARCHAR(64),
    estado         VARCHAR(16)   NOT NULL,
    version        BIGINT        NOT NULL,
    creado_en      TIMESTAMPTZ   NOT NULL,
    actualizado_en TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_evidencia_jrv_cargo ON evidencia (jrv_id, cargo_id);
CREATE INDEX ix_evidencia_usuario ON evidencia (usuario_id);
CREATE INDEX ix_evidencia_estado ON evidencia (estado);
-- rollback DROP TABLE evidencia;
