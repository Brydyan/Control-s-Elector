--liquibase formatted sql

-- ============================================================================
-- 010-02 — Asegurar secuencias JPA explícitamente y dar permisos
--
-- Las tablas creadas con BIGSERIAL generan la secuencia automáticamente en
-- Postgres, pero:
--
-- 1. BIGSERIAL NO establece DEFAULT explícito en la columna: Postgres lo
--    maneja internamente. Cuando Hibernate valida el schema, busca el DEFAULT
--    y lo necesita para saber que la secuencia está conectada.
--
-- 2. Los permisos pueden quedar limitados cuando Liquibase corre con un
--    usuario diferente al runtime (electoral_user para migraciones vs
--    electoral_app para la API).
--
-- Este changeset:
--   - Crea la secuencia explícitamente si no existe
--   - Fuerza SET DEFAULT (idempotente, se ejecuta siempre)
--   - Marca propiedad de la secuencia a la columna
--   - Da permisos AQUÍ, no espera a 036 (si 036 no corre, 010-02 lo cubre)
--
-- Todo junto garantiza que Hibernate encuentra la secuencia y el rol de
-- aplicación puede usarla desde el primer arranque.
-- ============================================================================

-- changeset electoral:010-02-audit-log-sequence splitStatements:false
DO $$
BEGIN
  -- Crear secuencia si no existe (idempotente)
  IF NOT EXISTS (SELECT 1 FROM pg_sequences WHERE sequencename = 'audit_log_id_seq') THEN
    CREATE SEQUENCE audit_log_id_seq;
  END IF;

  -- Forzar que audit_log.id use la secuencia (idempotente)
  -- Se ejecuta siempre para garantizar que el DEFAULT está en su sitio,
  -- incluso en BDs donde BIGSERIAL no lo estableció explícitamente.
  EXECUTE 'ALTER TABLE audit_log ALTER COLUMN id SET DEFAULT nextval(''audit_log_id_seq'')';

  -- Marcar propiedad (idempotente, falla silenciosamente si ya existe)
  BEGIN
    EXECUTE 'ALTER SEQUENCE audit_log_id_seq OWNED BY audit_log.id';
  EXCEPTION WHEN OTHERS THEN
    NULL; -- Ya está marcada
  END;

  -- Dar permisos al rol de aplicación si existe (idempotente)
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'electoral_app') THEN
    EXECUTE 'GRANT USAGE, SELECT ON SEQUENCE audit_log_id_seq TO electoral_app';
  END IF;
END $$;

-- rollback DROP SEQUENCE IF EXISTS audit_log_id_seq;
