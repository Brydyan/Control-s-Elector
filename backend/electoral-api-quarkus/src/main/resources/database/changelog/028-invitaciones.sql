--liquibase formatted sql

-- =============================================================================
-- 028 — Invitaciones de usuario por email (T-AUTH-INV-01).
--
-- Agrega a `usuario` el estado de activacion y las marcas de la invitacion:
--
--   estado_usuario          PENDIENTE_INVITACION | ACTIVO
--   activation_token_hash   HMAC del token de activacion vigente (NULL = consumido)
--   activated_at            momento de la activacion
--   invitation_sent_at      momento del ultimo envio de invitacion
--
-- ── Por que `estado_usuario` arranca en 'ACTIVO' y no en 'PENDIENTE_INVITACION' ──
--
-- La adicion de la columna con DEFAULT 'PENDIENTE_INVITACION' reescribiria TODAS
-- las filas existentes de `usuario` con ese valor. En este repo las filas
-- existentes no son un caso de borde: son las cinco cuentas de referencia
-- (MASTER, COORDINATOR, ADMIN y dos OBSERVERs) que son la unica forma de
-- entrar al sistema, mas todos los observadores que el MASTER ya creo.
--
-- Con ese DEFAULT, en el instante de la migracion los cinco usuarios de
-- referencia pasarian de ACTIVO a PENDIENTE_INVITACION, el LoginUseCase
-- rechazaria su login con 403 y el sistema electoral entero quedaria sin
-- ninguna cuenta con la que entrar — sin password temporal, sin invitacion,
-- sin forma de reentrar. Un DEFAULT que bloquea el arranque.
--
-- El default correcto es 'ACTIVO': las cuentas que ya existian estan activas
-- (es lo unico que el sistema ha conocido hasta ahora) y el estado
-- PENDIENTE_INVITACION se asigna de forma explicita, en el mismo
-- INSERT/Update que crea al usuario invitado. El flujo de invitacion es
-- opt-in por usuario, nunca por defecto de columna.
--
-- ── Por que `activation_token_hash` y no `activation_token` ──
--
-- El token de activacion es un bearer secret: quien lo tiene puede tomar la
-- cuenta. Guardarlo en claro en la base significa que un dump de `usuario`
-- entrega invitaciones activables durante la ventana de 24 horas. Se guarda
-- el HMAC-SHA256 del token (mismo patron que `sesion.refresh_token_hash` con
-- `app.jwt.refresh-pepper`), de modo que la base no contiene ningun token
-- utilizable y la comparacion se hace por hash.
--
-- El hash ocupa 43 caracteres (Base64Url sin padding de 32 bytes) y el token
-- nunca se necesita leer de vuelta: se verifica recalculando el hash del
-- token recibido y comparando con el almacenado.
--
-- La columna es NULL cuando no hay invitacion vigente. Ese NULL es
-- justamente la marca de "token consumido" (decision D4 del design): tras
-- activar, el token se anula y un segundo POST /activate con el mismo token
-- no puede pasar la comparacion.
-- =============================================================================

-- changeset electoral:028-01-estado-usuario
ALTER TABLE usuario ADD COLUMN estado_usuario VARCHAR(32) NOT NULL DEFAULT 'ACTIVO';
-- rollback: ALTER TABLE usuario DROP COLUMN estado_usuario;

-- changeset electoral:028-02-activation-token-hash
ALTER TABLE usuario ADD COLUMN activation_token_hash VARCHAR(64);
-- rollback: ALTER TABLE usuario DROP COLUMN activation_token_hash;

-- changeset electoral:028-03-marcas-de-tiempo
ALTER TABLE usuario ADD COLUMN activated_at TIMESTAMPTZ;
ALTER TABLE usuario ADD COLUMN invitation_sent_at TIMESTAMPTZ;
-- rollback: ALTER TABLE usuario DROP COLUMN activated_at;
-- rollback: ALTER TABLE usuario DROP COLUMN invitation_sent_at;

-- changeset electoral:028-04-indexes
-- `idx_usuario_estado_usuario` existe porque el panel de MASTER filtra por
-- "usuarios pendientes de activar" y cada consulta cuenta sobre la columna.
-- Sin indice es un seq scan sobre la tabla de observadores el dia de la
-- eleccion.
CREATE INDEX idx_usuario_estado_usuario ON usuario (estado_usuario);
-- rollback: DROP INDEX idx_usuario_estado_usuario;

-- `idx_usuario_activation_token` existe porque la activacion busca por hash
-- de token: es el punto de entrada de un POST publico y sin indice seria un
-- seq scan por cada intento de activacion — un vector de denegacion de
-- servicio trivial de explotar.
CREATE INDEX idx_usuario_activation_token ON usuario (activation_token_hash);
-- rollback: DROP INDEX idx_usuario_activation_token;
