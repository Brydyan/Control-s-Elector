--liquibase formatted sql

-- changeset electoral:050-01-fix-get-current-user-id splitStatements:false
-- current_setting('app.current_user_id', true) devuelve '' (string vacío) o NULL si no está definido.
-- El casteo directo (''::BIGINT) falla con "invalid input syntax for type bigint: ''".
-- Usamos NULLIF(..., '') para transformar cadenas vacías en NULL antes de castear.
CREATE OR REPLACE FUNCTION get_current_user_id() RETURNS BIGINT AS $$
  SELECT (NULLIF(current_setting('app.current_user_id', true), ''))::BIGINT;
$$ LANGUAGE SQL STABLE;

ALTER FUNCTION get_current_user_id() SECURITY DEFINER;
ALTER FUNCTION get_current_user_id() SET search_path = public, pg_temp;

-- changeset electoral:050-02-recinto-jrv-rls
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