--liquibase formatted sql

-- changeset electoral:050-01-recinto-jrv-rls
-- Las tablas de configuración geográfica son compartidas por los roles autenticados, pero solo
-- MASTER puede mutarlas. Las escrituras activan app.current_user_id desde la capa de aplicación.
ALTER TABLE recinto ENABLE ROW LEVEL SECURITY;
ALTER TABLE recinto FORCE ROW LEVEL SECURITY;
ALTER TABLE jrv ENABLE ROW LEVEL SECURITY;
ALTER TABLE jrv FORCE ROW LEVEL SECURITY;
ALTER TABLE jrv_cargo ENABLE ROW LEVEL SECURITY;
ALTER TABLE jrv_cargo FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_recinto_select ON recinto FOR SELECT USING (true);
CREATE POLICY rls_recinto_insert ON recinto FOR INSERT
  WITH CHECK (get_current_user_rol_name() = 'MASTER');
CREATE POLICY rls_recinto_update ON recinto FOR UPDATE
  USING (get_current_user_rol_name() = 'MASTER')
  WITH CHECK (get_current_user_rol_name() = 'MASTER');
CREATE POLICY rls_recinto_delete ON recinto FOR DELETE
  USING (get_current_user_rol_name() = 'MASTER');

CREATE POLICY rls_jrv_select ON jrv FOR SELECT USING (true);
CREATE POLICY rls_jrv_insert ON jrv FOR INSERT
  WITH CHECK (get_current_user_rol_name() = 'MASTER');
CREATE POLICY rls_jrv_update ON jrv FOR UPDATE
  USING (get_current_user_rol_name() = 'MASTER')
  WITH CHECK (get_current_user_rol_name() = 'MASTER');
CREATE POLICY rls_jrv_delete ON jrv FOR DELETE
  USING (get_current_user_rol_name() = 'MASTER');

CREATE POLICY rls_jrv_cargo_select ON jrv_cargo FOR SELECT USING (true);
CREATE POLICY rls_jrv_cargo_insert ON jrv_cargo FOR INSERT
  WITH CHECK (get_current_user_rol_name() = 'MASTER');
CREATE POLICY rls_jrv_cargo_update ON jrv_cargo FOR UPDATE
  USING (get_current_user_rol_name() = 'MASTER')
  WITH CHECK (get_current_user_rol_name() = 'MASTER');
CREATE POLICY rls_jrv_cargo_delete ON jrv_cargo FOR DELETE
  USING (get_current_user_rol_name() = 'MASTER');
-- rollback ALTER TABLE recinto NO FORCE ROW LEVEL SECURITY; ALTER TABLE recinto NO ROW LEVEL SECURITY;
-- rollback ALTER TABLE jrv NO FORCE ROW LEVEL SECURITY; ALTER TABLE jrv NO ROW LEVEL SECURITY;
-- rollback ALTER TABLE jrv_cargo NO FORCE ROW LEVEL SECURITY; ALTER TABLE jrv_cargo NO ROW LEVEL SECURITY;