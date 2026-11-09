--liquibase formatted sql

-- ============================================================================
-- 026 — Legal compliance (T-LEGAL-01)
--
-- Qué agrega:
--   1. Columnas de aceptación en `usuario` (qué versión aceptó, cuándo).
--   2. `terms_acceptance_audit`: historial COMPLETO de aceptaciones.
--   3. `dpo_contact_requests`: solicitudes al Delegado de Protección de Datos.
--   4. `user_export_log`: auditoría de las exportaciones de datos (GDPR Art. 15).
--   5. RLS de las tres tablas nuevas.
--
-- Por qué una tabla de auditoría aparte y no solo columnas en `usuario`:
-- la columna dice "qué versión aceptó este usuario"; el audit dice "qué aceptó
-- este usuario, cuándo, desde qué IP y con qué cliente". Si una versión se
-- publica con un error y hay que probar qué Saw cada persona, el segundo
-- conjunto es el que sirve. El primero es un atajo para no tener que consultar
-- el historial en cada login.
--
-- Las columnas son aditivas: no se tocan filas existentes ni se altera ningún
-- tipo. `accepted_terms_version` arranca en NULL, que significa "nunca aceptó".
-- Los usuarios que ya existían aceptan la versión vigente en su próximo login.
--
-- RLS: las tres tablas guardan datos personales (IP, user agent, mensajes).
-- Sin RLS, un endpoint que las lea sería una fuga horizontal: un OBSERVER
-- podría ver las aceptaciones de otros. Por eso las tres quedan con RLS FORCED
-- (el owner tampoco las ve: el acceso pasa por el contexto de la request).
-- ============================================================================


-- changeset electoral:026-01-usuario-acceptance-columns
ALTER TABLE usuario ADD COLUMN accepted_terms_version VARCHAR(10);
ALTER TABLE usuario ADD COLUMN accepted_terms_at TIMESTAMPTZ;
ALTER TABLE usuario ADD COLUMN accepted_privacy_version VARCHAR(10);
ALTER TABLE usuario ADD COLUMN accepted_privacy_at TIMESTAMPTZ;
-- rollback ALTER TABLE usuario DROP COLUMN accepted_privacy_at; ALTER TABLE usuario DROP COLUMN accepted_privacy_version; ALTER TABLE usuario DROP COLUMN accepted_terms_at; ALTER TABLE usuario DROP COLUMN accepted_terms_version;


-- changeset electoral:026-02-terms-acceptance-audit
CREATE TABLE terms_acceptance_audit (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  terms_version VARCHAR(10) NOT NULL,
  privacy_version VARCHAR(10),
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ip_address VARCHAR(45),
  user_agent VARCHAR(500),
  locale VARCHAR(16),
  consent_type VARCHAR(50) NOT NULL DEFAULT 'initial'
);
CREATE INDEX idx_terms_acceptance_user ON terms_acceptance_audit(usuario_id);
CREATE INDEX idx_terms_acceptance_version ON terms_acceptance_audit(terms_version);
CREATE INDEX idx_terms_acceptance_accepted_at ON terms_acceptance_audit(accepted_at);
-- rollback DROP TABLE terms_acceptance_audit;


-- changeset electoral:026-03-dpo-contact-requests
CREATE TABLE dpo_contact_requests (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(254) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ip_address VARCHAR(45),
  status VARCHAR(50) NOT NULL DEFAULT 'NEW',
  dpo_response TEXT,
  responded_at TIMESTAMPTZ
);
CREATE INDEX idx_dpo_status ON dpo_contact_requests(status);
CREATE INDEX idx_dpo_submitted ON dpo_contact_requests(submitted_at);
-- rollback DROP TABLE dpo_contact_requests;


-- changeset electoral:026-04-user-export-log
CREATE TABLE user_export_log (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  export_id VARCHAR(36) NOT NULL UNIQUE,
  exported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ip_address VARCHAR(45),
  record_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_export_user ON user_export_log(usuario_id);
CREATE INDEX idx_export_id ON user_export_log(export_id);
CREATE INDEX idx_export_exported_at ON user_export_log(exported_at);
-- rollback DROP TABLE user_export_log;


-- changeset electoral:026-05-rls
ALTER TABLE terms_acceptance_audit ENABLE ROW LEVEL SECURITY; ALTER TABLE terms_acceptance_audit FORCE ROW LEVEL SECURITY;
ALTER TABLE dpo_contact_requests ENABLE ROW LEVEL SECURITY; ALTER TABLE dpo_contact_requests FORCE ROW LEVEL SECURITY;
ALTER TABLE user_export_log ENABLE ROW LEVEL SECURITY; ALTER TABLE user_export_log FORCE ROW LEVEL SECURITY;

-- Cada usuario ve su propia aceptación; el equipo legal (MASTER) ve todas.
DROP POLICY IF EXISTS rls_terms_acceptance_select ON terms_acceptance_audit;
CREATE POLICY rls_terms_acceptance_select ON terms_acceptance_audit FOR SELECT USING (usuario_id = get_current_user_id() OR get_current_user_rol_name() = 'MASTER');
DROP POLICY IF EXISTS rls_terms_acceptance_insert ON terms_acceptance_audit;
CREATE POLICY rls_terms_acceptance_insert ON terms_acceptance_audit FOR INSERT WITH CHECK (usuario_id = get_current_user_id());

-- El formulario del DPO es público: cualquiera puede escribir. Leer es del DPO.
DROP POLICY IF EXISTS rls_dpo_select ON dpo_contact_requests;
CREATE POLICY rls_dpo_select ON dpo_contact_requests FOR SELECT USING (get_current_user_rol_name() = 'MASTER');
DROP POLICY IF EXISTS rls_dpo_insert ON dpo_contact_requests;
CREATE POLICY rls_dpo_insert ON dpo_contact_requests FOR INSERT WITH CHECK (true);
DROP POLICY IF EXISTS rls_dpo_update ON dpo_contact_requests;
CREATE POLICY rls_dpo_update ON dpo_contact_requests FOR UPDATE USING (get_current_user_rol_name() = 'MASTER');

-- El export log es del propio usuario (y del DPO).
DROP POLICY IF EXISTS rls_user_export_select ON user_export_log;
CREATE POLICY rls_user_export_select ON user_export_log FOR SELECT USING (usuario_id = get_current_user_id() OR get_current_user_rol_name() = 'MASTER');
DROP POLICY IF EXISTS rls_user_export_insert ON user_export_log;
CREATE POLICY rls_user_export_insert ON user_export_log FOR INSERT WITH CHECK (usuario_id = get_current_user_id());
-- rollback ALTER TABLE user_export_log NO FORCE ROW LEVEL SECURITY; ALTER TABLE user_export_log NO ROW LEVEL SECURITY; ALTER TABLE dpo_contact_requests NO FORCE ROW LEVEL SECURITY; ALTER TABLE dpo_contact_requests NO ROW LEVEL SECURITY; ALTER TABLE terms_acceptance_audit NO FORCE ROW LEVEL SECURITY; ALTER TABLE terms_acceptance_audit NO ROW LEVEL SECURITY;
