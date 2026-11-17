--liquibase formatted sql

-- ============================================================================
-- 037 - Política de INSERCIÓN para `notificacion`.
--
-- La tabla `notificacion` tiene RLS activada y forzada, pero NO tenía política
-- de inserción: solo las de SELECT y UPDATE (011-rls.sql).
--
-- El comentario del código (`ActaNotificacionService`) decía lo contrario:
-- "Postgres permite la escritura cuando ninguna politica aplica a la tabla".
-- Eso es FALSO y es la diferencia entre RLS inerte y RLS real: en una tabla SIN
-- RLS no hay politica que aplique y la escritura pasa; en una tabla CON RLS
-- habilitada, la ausencia de politica de INSERT significa DENEGAR la escritura.
--
-- Con el rol de superusuario (dev y CI hasta ahora) el INSERT pasaba igual, y
-- el defecto dormía. Con un rol de aplicación sin privilegios, cada aviso a un
-- observador moría con:
--   ERROR: new row violates row-level security policy for table "notificacion"
--
-- La política es la MISMA que la de la auditoría: la escritura la decide la
-- aplicación (a quién se avisa), y la LECTURA sigue filtrada por rol y por
-- destinatario. Quien puede insertar un aviso es quien está en su sesión: un
-- COORDINATOR abre la disputa o un MASTER anula el acta.
-- ============================================================================

-- changeset electoral:037-01-notificacion-insert-policy
DROP POLICY IF EXISTS rls_notificacion_insert ON notificacion;
CREATE POLICY rls_notificacion_insert ON notificacion FOR INSERT WITH CHECK (get_current_user_rol_name() IN ('MASTER','COORDINATOR'));
-- rollback DROP POLICY IF EXISTS rls_notificacion_insert ON notificacion;
