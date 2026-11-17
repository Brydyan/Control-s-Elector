--liquibase formatted sql

-- ============================================================================
-- 038 - Políticas de UPDATE y DELETE para `evidencia`.
--
-- La tabla `evidencia` tenía RLS activada y forzada con políticas de SELECT y
-- de INSERT, pero NINGUNA de UPDATE ni de DELETE. En Postgres, una tabla con
-- RLS habilitada a la que no le aplica ninguna política para un comando
-- lo DENIEGA: no es "sin restricción", es "denegado".
--
-- Con el rol de superusuario (dev y CI hasta ahora) el paso no se notaba. Con
-- el rol de aplicación sin privilegios, la app:
--
--   - no podía cambiar el estado de una evidencia ajena (UPDATE → 0 filas → 500)
--   - no podía borrar una evidencia (DELETE → 0 filas → 500)
--
-- Las dos reglas son las que la aplicación ya respeta: quien tiene
-- `evidencia:update` o `evidencia:delete` puede actuar sobre la evidencia de
-- cualquiera (COORDINATOR y MASTER), y el autor puede actuar sobre la suya.
-- El WITH CHECK del UPDATE impide además que un coordinator o un master
-- cambien de dueño la fila: `usuario_id` no se toca.
-- ============================================================================

-- changeset electoral:038-01-evidencia-update-delete
DROP POLICY IF EXISTS rls_evidencia_update ON evidencia;
CREATE POLICY rls_evidencia_update ON evidencia FOR UPDATE
  USING (get_current_user_rol_name() IN ('MASTER','COORDINATOR') OR usuario_id = get_current_user_id())
  WITH CHECK (get_current_user_rol_name() IN ('MASTER','COORDINATOR') OR usuario_id = get_current_user_id());

DROP POLICY IF EXISTS rls_evidencia_delete ON evidencia;
CREATE POLICY rls_evidencia_delete ON evidencia FOR DELETE
  USING (get_current_user_rol_name() IN ('MASTER','COORDINATOR') OR usuario_id = get_current_user_id());
-- rollback DROP POLICY IF EXISTS rls_evidencia_update ON evidencia; DROP POLICY IF EXISTS rls_evidencia_delete ON evidencia;
