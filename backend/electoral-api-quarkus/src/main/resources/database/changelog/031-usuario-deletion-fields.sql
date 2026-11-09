--liquibase formatted sql

-- =============================================================================
-- 029 — Campos de eliminación de cuenta (T-COMPLIANCE-04, D1-D4).
--
-- Agrega a `usuario` los campos para soft-delete y recuperación:
--
--   deleted_at           momento cuando se solicito la eliminacion (NULL = no eliminada)
--   deletion_status      estado de la eliminacion: ACTIVO | DELETED_PENDING | DELETED_PERMANENT
--
-- ── Estados de deletion_status ──
--
-- - ACTIVO:             cuenta activa y funcional (estado normal)
-- - DELETED_PENDING:    usuario solicito eliminacion, con ventana de 30 dias para recuperar
-- - DELETED_PERMANENT:  hard-delete completado, usuario y datos personales eliminados
--
-- Durante DELETED_PENDING (soft-delete):
--   - Usuario NO puede iniciar sesion (login rechaza con 403)
--   - Puede recuperar clickeando link en email (POST /api/user/cancel-deletion/{token})
--   - Ventana de recuperacion: 7 dias
--   - Hard-delete automatico despues 30 dias via cron job
--
-- ── Por que dos columnas en lugar de un enum ──
--
-- deletion_status es el estado principal (ACTIVO | DELETED_PENDING | DELETED_PERMANENT).
-- deleted_at marca cuando se solicito la eliminacion, necesario para:
--   - Calcular si pasaron 30 dias (hard-delete)
--   - Recuperar snapshot de eliminacion en deletion_audit
--   - Queries de "proximos a hard-delete"
--
-- ── Por que DELETED_PERMANENT sigue siendo ACTIVO después de hard-delete ──
--
-- Si el usuario hace hard-delete (DELETE FROM usuario), la referencia en
-- acta_electoral queda "huerfana" (voter_id apunta a nada). DELETED_PERMANENT
-- no existe en la base porque el row se borra. deletion_audit registra
-- hard_deleted_at para auditoria.
-- =============================================================================

-- changeset electoral:029-01-deleted-at
ALTER TABLE usuario ADD COLUMN deleted_at TIMESTAMPTZ;
-- rollback: ALTER TABLE usuario DROP COLUMN deleted_at;

-- changeset electoral:029-02-deletion-status
-- El default es 'ACTIVO' por la misma razon que en 028: cuentas existentes
-- deben permanecer sin cambios.
ALTER TABLE usuario ADD COLUMN deletion_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVO';
-- rollback: ALTER TABLE usuario DROP COLUMN deletion_status;

-- changeset electoral:029-03-index-deletion-status
-- Index para las queries de soft-delete: filtrar por deletion_status = 'DELETED_PENDING'
-- durante login y durante el cron job.
CREATE INDEX idx_usuario_deletion_status ON usuario (deletion_status);
-- rollback: DROP INDEX idx_usuario_deletion_status;

-- changeset electoral:029-04-index-deleted-at
-- Index para el cron job que busca: deleted_at < NOW() - interval '30 days'
-- AND deletion_status = 'DELETED_PENDING'
CREATE INDEX idx_usuario_deleted_at ON usuario (deleted_at);
-- rollback: DROP INDEX idx_usuario_deleted_at;
