--liquibase formatted sql

-- ============================================================================
-- 010-02 — Asegurar secuencias JPA explícitamente
--
-- Las tablas creadas con BIGSERIAL generan la secuencia automáticamente en
-- Postgres. Sin embargo, cuando Liquibase corre con un usuario diferente al
-- runtime (electoral_prod_owner para migraciones vs electoral_app para la API),
-- los permisos pueden quedar limitados o la secuencia no ser accesible.
--
-- Este changeset crea las secuencias explícitamente si no existen, garantizando
-- que Hibernate las encuentra sin importar quién ejecute las migraciones.
-- ============================================================================

-- changeset electoral:010-02-audit-log-sequence splitStatements:false
CREATE SEQUENCE IF NOT EXISTS audit_log_id_seq;
ALTER TABLE audit_log ALTER COLUMN id SET DEFAULT nextval('audit_log_id_seq');
ALTER SEQUENCE audit_log_id_seq OWNED BY audit_log.id;
-- rollback DROP SEQUENCE IF EXISTS audit_log_id_seq;
