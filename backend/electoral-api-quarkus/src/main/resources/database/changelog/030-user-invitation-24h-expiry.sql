--liquibase formatted sql

-- =============================================================================
-- 030 — Invitaciones con 24 horas de vigencia (ODD #504, tarea B).
--
-- El SDD #492 dejo la invitacion con 30 dias de vigencia. El analisis de UX
-- lo corrigio a 24 horas, que es lo mismo que dura el token de activacion:
-- una invitacion que vive 30 dias es una credencial que vive 30 dias en un
-- buzon de correo.
--
-- ── Por que el UPDATE va con la RLS apagada ──
--
-- `user_invitations` tiene RLS activada y forzada, y la politica de UPDATE
-- solo deja escribir a la persona de la fila o al MASTER. La migracion corre
-- con el usuario de la aplicacion, que no es superusuario en produccion: sin
-- contexto de RLS, el UPDATE no tocaria ninguna fila y la migracion pasaria
-- en silencio sin hacer su trabajo.
--
-- Por eso el UPDATE va con la RLS apagada y se vuelve a prender en el mismo
-- changeset: el dueno de la tabla puede alternar la RLS de su propia tabla, y
-- la migracion termina con la tabla igual de protegida que estaba.
-- =============================================================================


-- changeset electoral:030-01-expiry-24h-default
ALTER TABLE user_invitations
  ALTER COLUMN expiry_at SET DEFAULT (NOW() + INTERVAL '1 day');
-- rollback: ALTER TABLE user_invitations ALTER COLUMN expiry_at SET DEFAULT (NOW() + INTERVAL '30 days');


-- changeset electoral:030-02-expiry-24h-migracion
ALTER TABLE user_invitations DISABLE ROW LEVEL SECURITY;
UPDATE user_invitations
   SET expiry_at = invited_at + INTERVAL '1 day'
 WHERE expiry_at > invited_at + INTERVAL '1 day';
ALTER TABLE user_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_invitations FORCE ROW LEVEL SECURITY;
-- rollback: ALTER TABLE user_invitations ALTER COLUMN expiry_at SET DEFAULT (NOW() + INTERVAL '30 days');
