-- Infra SQL 02 — Row Level Security (RLS)
-- Garantiza que cada usuario solo ve sus filas según su rol y asignaciones

-- ============================================================================
-- SETUP: Extensión y funciones para leer contexto de usuario
-- ============================================================================

-- UUID extension si no existe
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Función que obtiene el usuario ID actual de la session variable
-- El API hace: SET LOCAL app.current_user_id = '123' antes de queries
CREATE OR REPLACE FUNCTION get_current_user_id()
RETURNS BIGINT AS $$
  SELECT (current_setting('app.current_user_id', true))::BIGINT;
$$ LANGUAGE SQL STABLE;

-- Función que obtiene el rol del usuario actual
CREATE OR REPLACE FUNCTION get_current_user_rol()
RETURNS VARCHAR AS $$
  SELECT rol_id FROM usuario WHERE id = get_current_user_id();
$$ LANGUAGE SQL STABLE;

-- ============================================================================
-- ENABLE RLS EN TABLAS SENSIBLES
-- ============================================================================

ALTER TABLE acta ENABLE ROW LEVEL SECURITY;
ALTER TABLE acta_detalle ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE notificacion ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- POLICIES: ACTA
-- ============================================================================

-- Policy 1: OBSERVER solo puede VER actas de sus JRVs
CREATE POLICY rls_acta_observer_select ON acta
  FOR SELECT
  USING (
    -- Si el usuario es OBSERVER:
    (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) = 'OBSERVER'
    AND jrv_id IN (
      -- Su jrv_id está en observer_jrv_asignacion
      SELECT jrv_id FROM observer_jrv_asignacion
      WHERE observer_id = get_current_user_id()
    )
  )
  OR
  -- Si es COORDINATOR, ADMIN o MASTER: ve todo
  (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) IN ('COORDINATOR', 'ADMIN', 'MASTER');

-- Policy 2: OBSERVER solo puede INSERT actas en sus JRVs
CREATE POLICY rls_acta_observer_insert ON acta
  FOR INSERT
  WITH CHECK (
    (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) = 'OBSERVER'
    AND jrv_id IN (
      SELECT jrv_id FROM observer_jrv_asignacion
      WHERE observer_id = get_current_user_id()
    )
  )
  OR
  (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) IN ('COORDINATOR', 'MASTER');

-- Policy 3: OBSERVER solo puede UPDATE sus propias actas, COORDINATOR/MASTER pueden actualizar cualquiera
CREATE POLICY rls_acta_observer_update ON acta
  FOR UPDATE
  USING (
    (
      (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) = 'OBSERVER'
      AND registrada_por = get_current_user_id()
    )
    OR
    (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) IN ('COORDINATOR', 'MASTER')
  );

-- ============================================================================
-- POLICIES: ACTA_DETALLE (heredar acceso de su acta)
-- ============================================================================

CREATE POLICY rls_acta_detalle_select ON acta_detalle
  FOR SELECT
  USING (
    acta_id IN (
      SELECT id FROM acta WHERE
        (
          (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) = 'OBSERVER'
          AND jrv_id IN (
            SELECT jrv_id FROM observer_jrv_asignacion
            WHERE observer_id = get_current_user_id()
          )
        )
        OR
        (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) IN ('COORDINATOR', 'ADMIN', 'MASTER')
    )
  );

CREATE POLICY rls_acta_detalle_insert ON acta_detalle
  FOR INSERT
  WITH CHECK (
    acta_id IN (
      SELECT id FROM acta WHERE
        (
          (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) = 'OBSERVER'
          AND jrv_id IN (
            SELECT jrv_id FROM observer_jrv_asignacion
            WHERE observer_id = get_current_user_id()
          )
        )
        OR
        (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) IN ('COORDINATOR', 'MASTER')
    )
  );

-- ============================================================================
-- POLICIES: EVIDENCIA (fotos)
-- ============================================================================

CREATE POLICY rls_evidencia_select ON evidencia
  FOR SELECT
  USING (
    (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) IN ('COORDINATOR', 'ADMIN', 'MASTER')
    OR
    -- OBSERVER ve fotos de su JRVs
    (
      (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) = 'OBSERVER'
      AND jrv_id IN (
        SELECT jrv_id FROM observer_jrv_asignacion
        WHERE observer_id = get_current_user_id()
      )
    )
  );

CREATE POLICY rls_evidencia_insert ON evidencia
  FOR INSERT
  WITH CHECK (
    usuario_id = get_current_user_id()  -- Solo puede subir fotos propias
    AND (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) = 'OBSERVER'
  );

-- ============================================================================
-- POLICIES: AUDIT_LOG (lectura restringida)
-- ============================================================================

CREATE POLICY rls_audit_log_select ON audit_log
  FOR SELECT
  USING (
    -- Solo COORDINATOR, ADMIN, MASTER ven audit log
    (SELECT r.nombre FROM usuario u LEFT JOIN rol r ON u.rol_id = r.id WHERE u.id = get_current_user_id()) IN ('COORDINATOR', 'ADMIN', 'MASTER')
  );

-- ============================================================================
-- POLICIES: NOTIFICACION (avisos personales)
-- ============================================================================

CREATE POLICY rls_notificacion_select ON notificacion
  FOR SELECT
  USING (
    -- Solo ve tus propias notificaciones
    observer_id = get_current_user_id()
  );

CREATE POLICY rls_notificacion_update ON notificacion
  FOR UPDATE
  USING (observer_id = get_current_user_id())
  WITH CHECK (observer_id = get_current_user_id());

-- ============================================================================
-- VERIFICACIÓN (comentado — ejecutar manualmente si necesitas)
-- ============================================================================

-- SELECT tablename, policyname, permissive, roles, qual, with_check
-- FROM pg_policies
-- ORDER BY tablename;
