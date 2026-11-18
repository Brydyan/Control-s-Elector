--liquibase formatted sql

-- ============================================================================
-- 010-03 — Reparar audit_log.id DEFAULT si está faltando
--
-- En BDs antiguas (creadas antes de que 010-02 fuera completo), la columna
-- audit_log.id puede no tener DEFAULT explícito. Esto causa que Hibernate
-- falle en validación de schema: "missing sequence [audit_log_id_seq]".
--
-- Este changeset detecta si el DEFAULT está faltando y lo agrega.
-- Si ya está presente, no hace nada (idempotente).
-- ============================================================================

-- changeset electoral:010-03-audit-log-sequence-repair splitStatements:false
DO $$
BEGIN
  -- Reparar solo si el DEFAULT no existe en la columna
  -- (Detecta BDs donde BIGSERIAL creó la secuencia pero no estableció DEFAULT explícito)
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'audit_log'
      AND column_name = 'id'
      AND column_default IS NOT NULL
      AND column_default LIKE 'nextval%audit_log_id_seq%'
  ) THEN
    -- Forzar que audit_log.id use la secuencia
    EXECUTE 'ALTER TABLE audit_log ALTER COLUMN id SET DEFAULT nextval(''audit_log_id_seq'')';
    RAISE NOTICE 'Reparado: audit_log.id ahora tiene DEFAULT nextval(''audit_log_id_seq'')';
  ELSE
    RAISE NOTICE 'OK: audit_log.id ya tiene DEFAULT configurado';
  END IF;
END $$;

-- rollback (no es reversible, el changeset es reparador)
