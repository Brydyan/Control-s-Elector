--liquibase formatted sql

-- =============================================================================
-- 046 — User Profile Settings (T-AUTH-SETTINGS-01): email_personal único
--
-- Agrega restricción UNIQUE a `usuario.email_personal` para permitir que
-- múltiples usuarios tengan email_personal NULL (SQL standard), pero no
-- dos usuarios distintos con el mismo email_personal.
--
-- La columna ya existe desde 029-user-invitations.sql, pero sin restricción.
-- Este changeset la agrega de forma idempotente: solo aplica si no existe.
--
-- Notas de Diseño (D5):
-- - email_personal debe ser único en el sistema (si no es NULL)
-- - Mantiene integridad de datos de contacto
-- - Índice UNIQUE en PostgreSQL permite múltiples NULLs (SQL standard)
-- =============================================================================

-- changeset electoral:046-01-email-personal-unique-index
CREATE UNIQUE INDEX idx_usuario_email_personal ON usuario(email_personal) WHERE email_personal IS NOT NULL;
-- rollback: DROP INDEX idx_usuario_email_personal;

-- changeset electoral:046-02-email-personal-unique-constraint
ALTER TABLE usuario ADD CONSTRAINT uq_usuario_email_personal UNIQUE (email_personal) DEFERRABLE INITIALLY DEFERRED;
-- rollback: ALTER TABLE usuario DROP CONSTRAINT uq_usuario_email_personal;
