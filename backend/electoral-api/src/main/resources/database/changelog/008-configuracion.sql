--liquibase formatted sql

-- ============================================================================
-- 008 - Configuracion preelectoral: Cargo, Partido, Candidato, Jrv_Cargo.
--
-- Refleja las entidades JPA del package ec.twintailcs.electoral.electoral.domain:
--   Cargo, Partido, Candidato, JrvCargo (+ JrvCargoId embeddable).
--
-- Estas tablas ya eran referenciadas por las entidades JPA (T-BE-09) pero
-- nunca fueron creadas via Liquibase. spring.jpa.hibernate.ddl-auto=validate
-- exige que el schema exista antes de levantar el contexto.
-- ============================================================================


-- changeset electoral:008-01-create-cargo
CREATE TABLE cargo (
    id      BIGSERIAL     PRIMARY KEY,
    nombre  VARCHAR(100)  NOT NULL,
    nivel   VARCHAR(20)   NOT NULL,
    puestos INTEGER       NOT NULL
);
-- rollback DROP TABLE cargo;


-- changeset electoral:008-02-create-partido
CREATE TABLE partido (
    id            BIGSERIAL     PRIMARY KEY,
    nombre        VARCHAR(150)  NOT NULL,
    siglas        VARCHAR(20),
    numero_lista  INTEGER
);
CREATE UNIQUE INDEX ix_partido_numero_lista ON partido (numero_lista);
-- rollback DROP TABLE partido;


-- changeset electoral:008-03-create-candidato
CREATE TABLE candidato (
    id          BIGSERIAL     PRIMARY KEY,
    nombre      VARCHAR(200)  NOT NULL,
    orden       INTEGER       NOT NULL,
    cargo_id    BIGINT        NOT NULL,
    partido_id  BIGINT        NOT NULL,
    CONSTRAINT fk_candidato_cargo FOREIGN KEY (cargo_id) REFERENCES cargo (id),
    CONSTRAINT fk_candidato_partido FOREIGN KEY (partido_id) REFERENCES partido (id)
);
CREATE INDEX ix_candidato_cargo ON candidato (cargo_id);
CREATE INDEX ix_candidato_partido ON candidato (partido_id);
-- rollback DROP TABLE candidato;


-- changeset electoral:008-04-create-jrv-cargo
CREATE TABLE jrv_cargo (
    jrv_id   BIGINT   NOT NULL,
    cargo_id BIGINT   NOT NULL,
    activo   BOOLEAN  NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_jrv_cargo PRIMARY KEY (jrv_id, cargo_id),
    CONSTRAINT fk_jrv_cargo_jrv FOREIGN KEY (jrv_id) REFERENCES jrv (id),
    CONSTRAINT fk_jrv_cargo_cargo FOREIGN KEY (cargo_id) REFERENCES cargo (id)
);
-- rollback DROP TABLE jrv_cargo;


-- changeset electoral:008-05-permiso-configuracion
-- Un unico permiso 'configuracion:write' cubre create/update/delete de
-- cargo/partido/candidato. La lectura (GET) queda abierta a cualquier
-- usuario autenticado (isAuthenticated()), sin permiso dedicado.
INSERT INTO permiso (recurso, accion) VALUES
    ('configuracion', 'write')
ON CONFLICT (recurso, accion) DO NOTHING;
-- rollback DELETE FROM permiso WHERE recurso = 'configuracion' AND accion = 'write';


-- changeset electoral:008-06-rol-permiso-configuracion
-- Solo MASTER puede escribir configuracion preelectoral.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.nombre = 'MASTER'
  AND p.recurso = 'configuracion' AND p.accion = 'write'
  AND NOT EXISTS (
      SELECT 1 FROM rol_permiso rp WHERE rp.rol_id = r.id AND rp.permiso_id = p.id
  );
-- rollback DELETE FROM rol_permiso WHERE rol_id = (SELECT id FROM rol WHERE nombre = 'MASTER')
--   AND permiso_id = (SELECT id FROM permiso WHERE recurso = 'configuracion' AND accion = 'write');
