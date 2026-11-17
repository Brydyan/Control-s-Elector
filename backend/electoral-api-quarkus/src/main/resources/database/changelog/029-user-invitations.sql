--liquibase formatted sql

-- =============================================================================
-- 029 — Invitaciones de usuario con email personal (T-AUTH-INV-02).
--
-- Agrega:
--
--   1. `usuario.email_personal`: el correo de contacto de la persona invitada.
--   2. `user_invitations`: la auditoria de cada invitacion (quien invita, a que
--      direccion, desde que IP, cuando expira, cuando se activo).
--
-- ── Por que `email_personal` es una columna nueva y no se reutiliza `email` ──
--
-- En este sistema el correo es la IDENTIDAD de acceso: `usuario.email` es el
-- login que el observador escribe en la pantalla de inicio de sesion, y se
-- genera solo a partir de nombre, apellido y rol (`juaperobs@control.ttcs`).
-- El "email personal" del SDD es otra cosa: el correo donde la persona recibe
-- la invitacion y por el que se puede contactar. Son dos datos distintos, con
-- distinto ciclo de vida: la identidad no cambia, el contacto si.
--
-- Por eso la columna es nullable y arranca en NULL: las cinco cuentas de
-- referencia y todos los observadores que el MASTER ya creo no tienen correo
-- personal, y una columna NOT NULL sin default los dejaria fuera del paso.
--
-- ── Por que `user_invitations` y no solo las marcas de `usuario` ──
--
-- `usuario` ya tiene `invitation_sent_at` y `activation_token_hash`: son el
-- estado de la invitacion VIGENTE. Lo que falta es el HISTORIAL: quien
--ْinvito, a que direccion personal, desde que IP, cuando expiro la
-- invitacion y cuando se activo. Sin esa fila, la pregunta "quien trajo a esta
-- persona al sistema y con que direccion" no tiene respuesta, y esa es
-- justamente la auditoria que el SDD pide.
--
-- ── RLS ──
--
-- La tabla guarda datos personales (email de contacto, IP, agente de usuario),
-- asi que lleva RLS activada y forzada como las demas: sin esto, un endpoint
-- que la lea seria una fuga horizontal y un OBSERVER podria ver las
-- invitaciones de otros. La politica de lectura deja al propio invitado ver su
-- fila y al MASTER verlas todas; la de escritura exige que quien invita sea
-- quien hizo la invitacion.
-- =============================================================================


-- changeset electoral:029-01-usuario-email-personal
ALTER TABLE usuario ADD COLUMN email_personal VARCHAR(254);
-- rollback: ALTER TABLE usuario DROP COLUMN email_personal;


-- changeset electoral:029-02-user-invitations
CREATE TABLE user_invitations (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  email_personal VARCHAR(254) NOT NULL,
  invited_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  invited_by BIGINT REFERENCES usuario(id),
  status VARCHAR(20) NOT NULL DEFAULT 'INVITED',
  ip_address VARCHAR(45),
  user_agent VARCHAR(500),
  activated_at TIMESTAMPTZ,
  expiry_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '30 days')
);
-- `invited_by` no lleva ON DELETE CASCADE a proposito: si se borra el MASTER que
-- invitó, la fila se queda como historico (con `invited_by` en NULL) en vez de
-- desaparecer. El historico de invitaciones no se borra con las cuentas.
-- rollback: DROP TABLE user_invitations;


-- changeset electoral:029-03-indexes
-- `idx_user_invitations_usuario` existe porque la activacion busca la
-- invitacion vigente del usuario que esta activando la cuenta: es el punto de
-- entrada de un POST publico y sin indice seria un seq scan por intento.
CREATE INDEX idx_user_invitations_usuario ON user_invitations (usuario_id);
-- rollback: DROP INDEX idx_user_invitations_usuario;

-- `idx_user_invitations_status` existe porque el panel filtra por estado
-- ("invitaciones pendientes") y cuenta sobre la columna.
CREATE INDEX idx_user_invitations_status ON user_invitations (status);
-- rollback: DROP INDEX idx_user_invitations_status;

-- `idx_user_invitations_email` existe porque se busca por la direccion de
-- contacto (por ejemplo, para saber si a alguien ya se le invitio).
CREATE INDEX idx_user_invitations_email ON user_invitations (email_personal);
-- rollback: DROP INDEX idx_user_invitations_email;


-- changeset electoral:029-04-rls
ALTER TABLE user_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_invitations FORCE ROW LEVEL SECURITY;
-- rollback: ALTER TABLE user_invitations NO FORCE ROW LEVEL SECURITY; ALTER TABLE user_invitations NO ROW LEVEL SECURITY;

-- El invitado ve su propia invitacion; el equipo legal (MASTER) ve todas.
DROP POLICY IF EXISTS rls_user_invitations_select ON user_invitations;
CREATE POLICY rls_user_invitations_select ON user_invitations FOR SELECT USING (usuario_id = get_current_user_id() OR get_current_user_rol_name() = 'MASTER');

-- La escritura la hace quien invita: `invited_by` tiene que ser el usuario del
-- contexto de RLS. Sin esta condicion, un endpoint que inserte sin activar el
-- contexto no podria escribir (la politica lo rechaza) y, si lo activara con
-- otro id, podria atribuir la invitacion a otra persona.
DROP POLICY IF EXISTS rls_user_invitations_insert ON user_invitations;
CREATE POLICY rls_user_invitations_insert ON user_invitations FOR INSERT WITH CHECK (invited_by = get_current_user_id());

-- El paso a ACTIVATED (o a EXPIRED) lo escribe el sistema: lo hace el mismo
-- MASTER que invita o el propio invitado al activar la cuenta.
DROP POLICY IF EXISTS rls_user_invitations_update ON user_invitations;
CREATE POLICY rls_user_invitations_update ON user_invitations FOR UPDATE USING (usuario_id = get_current_user_id() OR get_current_user_rol_name() = 'MASTER');
