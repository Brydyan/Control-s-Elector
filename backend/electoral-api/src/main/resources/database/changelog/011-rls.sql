--liquibase formatted sql

-- changeset electoral:011-01-rls-functions
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE OR REPLACE FUNCTION get_current_user_id() RETURNS BIGINT AS $$ SELECT (current_setting('app.current_user_id', true))::BIGINT; $$ LANGUAGE SQL STABLE;
CREATE OR REPLACE FUNCTION get_current_user_rol_name() RETURNS TEXT AS $$ SELECT r.nombre::TEXT FROM usuario u JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id(); $$ LANGUAGE SQL STABLE;
-- rollback DROP FUNCTION get_current_user_rol_name(); DROP FUNCTION get_current_user_id();

-- changeset electoral:011-02-enable-rls
ALTER TABLE acta ENABLE ROW LEVEL SECURITY; ALTER TABLE acta FORCE ROW LEVEL SECURITY;
ALTER TABLE acta_detalle ENABLE ROW LEVEL SECURITY; ALTER TABLE acta_detalle FORCE ROW LEVEL SECURITY;
ALTER TABLE evidencia ENABLE ROW LEVEL SECURITY; ALTER TABLE evidencia FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY; ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
ALTER TABLE notificacion ENABLE ROW LEVEL SECURITY; ALTER TABLE notificacion FORCE ROW LEVEL SECURITY;
-- rollback ALTER TABLE acta NO FORCE ROW LEVEL SECURITY; ALTER TABLE acta NO ROW LEVEL SECURITY;

-- changeset electoral:011-03-policies
DROP POLICY IF EXISTS rls_acta_select ON acta; CREATE POLICY rls_acta_select ON acta FOR SELECT USING (get_current_user_rol_name() IN ('COORDINATOR','MASTER','ADMIN') OR registrada_por = get_current_user_id());
DROP POLICY IF EXISTS rls_acta_insert ON acta; CREATE POLICY rls_acta_insert ON acta FOR INSERT WITH CHECK (get_current_user_rol_name() IN ('COORDINATOR','MASTER') OR (get_current_user_rol_name() = 'OBSERVER' AND EXISTS (SELECT 1 FROM jrv_cargo WHERE jrv_id=acta.jrv_id AND cargo_id=acta.cargo_id AND activo=TRUE)));
DROP POLICY IF EXISTS rls_acta_update ON acta; CREATE POLICY rls_acta_update ON acta FOR UPDATE USING (get_current_user_rol_name() IN ('COORDINATOR','MASTER') OR registrada_por=get_current_user_id());
DROP POLICY IF EXISTS rls_acta_delete ON acta; CREATE POLICY rls_acta_delete ON acta FOR DELETE USING (get_current_user_rol_name()='MASTER');
DROP POLICY IF EXISTS rls_acta_detalle_select ON acta_detalle; CREATE POLICY rls_acta_detalle_select ON acta_detalle FOR SELECT USING (EXISTS (SELECT 1 FROM acta a WHERE a.id=acta_id));
DROP POLICY IF EXISTS rls_acta_detalle_insert ON acta_detalle; CREATE POLICY rls_acta_detalle_insert ON acta_detalle FOR INSERT WITH CHECK (EXISTS (SELECT 1 FROM acta a WHERE a.id=acta_id));
DROP POLICY IF EXISTS rls_acta_detalle_update ON acta_detalle; CREATE POLICY rls_acta_detalle_update ON acta_detalle FOR UPDATE USING (EXISTS (SELECT 1 FROM acta a WHERE a.id=acta_id));
DROP POLICY IF EXISTS rls_acta_detalle_delete ON acta_detalle; CREATE POLICY rls_acta_detalle_delete ON acta_detalle FOR DELETE USING (EXISTS (SELECT 1 FROM acta a WHERE a.id=acta_id));
DROP POLICY IF EXISTS rls_evidencia_select ON evidencia; CREATE POLICY rls_evidencia_select ON evidencia FOR SELECT USING (get_current_user_rol_name() IN ('COORDINATOR','MASTER','ADMIN') OR usuario_id=get_current_user_id());
DROP POLICY IF EXISTS rls_evidencia_insert ON evidencia; CREATE POLICY rls_evidencia_insert ON evidencia FOR INSERT WITH CHECK (usuario_id=get_current_user_id());
DROP POLICY IF EXISTS rls_audit_log_select ON audit_log; CREATE POLICY rls_audit_log_select ON audit_log FOR SELECT USING (get_current_user_rol_name() IN ('COORDINATOR','MASTER','ADMIN'));
DROP POLICY IF EXISTS rls_audit_log_insert ON audit_log; CREATE POLICY rls_audit_log_insert ON audit_log FOR INSERT WITH CHECK (true);
DROP POLICY IF EXISTS rls_notificacion_select ON notificacion; CREATE POLICY rls_notificacion_select ON notificacion FOR SELECT USING (destinatario_id=get_current_user_id() OR get_current_user_rol_name() IN ('MASTER','COORDINATOR'));
DROP POLICY IF EXISTS rls_notificacion_update ON notificacion; CREATE POLICY rls_notificacion_update ON notificacion FOR UPDATE USING (destinatario_id=get_current_user_id() OR get_current_user_rol_name() IN ('MASTER','COORDINATOR'));
-- rollback DROP POLICY IF EXISTS rls_acta_select ON acta;
