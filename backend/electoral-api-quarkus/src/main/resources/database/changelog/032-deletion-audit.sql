--liquibase formatted sql

-- =============================================================================
-- 030 — Tabla de auditoría de eliminaciones (T-COMPLIANCE-04, D4).
--
-- deletion_audit registra un snapshot de cada solicitud/recuperacion/hard-delete.
--
-- Columnas:
--   id                    PK auto-incrementado
--   usuario_id            FK a usuario.id (no se refuerza: puede ser huerfana post-delete)
--   email_at_deletion     snapshot del email en el momento de solicitud
--   name_at_deletion      snapshot del nombre (nombre + apellido concatenado)
--   deleted_at            cuando se solicito la eliminacion (T+0)
--   deletion_reason       razon de la eliminacion (ej: "user_request")
--   recovery_deadline     hasta cuando se puede recuperar (T+7d)
--   was_recovered         true si el usuario cancelo la eliminacion
--   recovered_at          cuando se cancelo (si fue cancelada)
--   hard_deleted_at       cuando se completo el hard-delete permanente (T+30d)
--   created_at            cuando se creo el registro (ej: para auditoria)
--
-- ── Flujo de estados ──
--
-- Usuario solicita delete:
--   INSERT deletion_audit {
--     usuario_id, email_at_deletion, deleted_at = NOW(),
--     recovery_deadline = NOW() + 7d, was_recovered = false, ...
--   }
--   UPDATE usuario SET deletion_status = 'DELETED_PENDING', deleted_at = NOW()
--
-- Usuario cancela (dentro 7d):
--   UPDATE deletion_audit SET was_recovered = true, recovered_at = NOW()
--   UPDATE usuario SET deletion_status = 'ACTIVO', deleted_at = NULL
--
-- Cron job (T+30d):
--   DELETE FROM usuario WHERE id = X
--   UPDATE deletion_audit SET hard_deleted_at = NOW()
-- =============================================================================

-- changeset electoral:030-01-create-deletion-audit
CREATE TABLE deletion_audit (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  email_at_deletion VARCHAR(254) NOT NULL,
  name_at_deletion VARCHAR(200),
  deleted_at TIMESTAMPTZ NOT NULL,
  deletion_reason VARCHAR(64) NOT NULL,
  recovery_deadline TIMESTAMPTZ NOT NULL,
  was_recovered BOOLEAN NOT NULL DEFAULT false,
  recovered_at TIMESTAMPTZ,
  hard_deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- rollback: DROP TABLE deletion_audit;

-- changeset electoral:030-02-index-usuario-id
-- Index para auditoria: buscar registros de un usuario especifico
CREATE INDEX idx_deletion_audit_usuario_id ON deletion_audit (usuario_id);
-- rollback: DROP INDEX idx_deletion_audit_usuario_id;

-- changeset electoral:030-03-index-hard-deleted-at
-- Index para recuperacion de datos: buscar registros que aun no fueron hard-deleted
CREATE INDEX idx_deletion_audit_hard_deleted_at ON deletion_audit (hard_deleted_at);
-- rollback: DROP INDEX idx_deletion_audit_hard_deleted_at;
