# T-IN-03 — RLS (Row Level Security) en PostgreSQL

**Estado:** COMPLETADO ✅
**Dificultad:** Avanzado
**Tiempo estimado:** 3 horas
**Depende de:** T-IN-02 ✅

---

## ¿Qué vas a construir?

Vas a implementar **Row Level Security (RLS)** en PostgreSQL: reglas automáticas que filtran filas según el usuario conectado. Sin RLS, un COORDINATOR vería TODAS las actas; con RLS, un OBSERVER solo verá las suyas (según `observer_jrv_asignacion`).

**Por qué es crítico:**
- Segunda línea de defensa (la primera es Spring Security en el API)
- Si el API falla autorizando, la BD rechaza igualmente
- Previene acceso indebido si alguien bypasea el API

---

## Conceptos clave

**¿Qué es Row Level Security (RLS)?**
PostgreSQL evalúa automáticamente qué filas cada usuario puede ver/modificar basándose en **policies**. Sin RLS, `SELECT * FROM acta` devuelve todas las actas. Con RLS, devuelve solo las que la policy permite.

**¿Qué es una "policy"?**
Una regla SQL que se ejecuta automáticamente antes de cada query. Ej: "OBSERVER solo puede VER actas cuya JRV esté en `observer_jrv_asignacion`".

**¿Cuál es la diferencia entre USING y WITH CHECK?**
- **USING**: controla qué filas puede VER el usuario en SELECT
- **WITH CHECK**: controla qué filas puede MODIFICAR (INSERT/UPDATE/DELETE)

**¿Qué es `CURRENT_USER` / `session_user`?**
Variables especiales que PostgreSQL proporciona. `CURRENT_USER` = email del usuario conectado en este momento.

**¿Cómo sabe RLS quién es el usuario?**
Via `session variables` que el API establece en cada query. Ej: el Java hace `SET LOCAL app.current_user_id = '123'` antes de SELECT.

**¿Qué es `SET LOCAL`?**
Establece una variable solo para esta transacción/query. Cuando termina, se resetea. Seguro porque no afecta otras conexiones.

---

## Archivos a crear/modificar

```
backend/electoral-api/src/main/resources/database/infra/sql/
  02-rls.sql                          ← Crear
  
backend/electoral-api/src/main/java/ec/twintailcs/electoral/
  shared/
    security/
      RlsInterceptor.java             ← Crear (interceptor que setea session var)
```

---

## Pasos

### Paso 1 — Crear `02-rls.sql`

Guarda en: `backend/electoral-api/src/main/resources/database/infra/sql/02-rls.sql`

```sql
-- Infra SQL 02 — Row Level Security (RLS)
-- Garantiza que cada usuario solo ve sus filas

-- ============================================================================
-- SETUP: Extensión y función para leer user_id de session variable
-- ============================================================================

-- UUID-OSP si no existe (para uuid_generate_v4())
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Función que obtiene el usuario ID actual de la session variable
-- El API hace: SET LOCAL app.current_user_id = '123' antes de queries
CREATE OR REPLACE FUNCTION get_current_user_id()
RETURNS BIGINT AS $$
  SELECT (current_setting('app.current_user_id', true))::BIGINT;
$$ LANGUAGE SQL STABLE;

-- Función que obtiene el rol del usuario
CREATE OR REPLACE FUNCTION get_current_user_rol()
RETURNS VARCHAR AS $$
  SELECT rol FROM usuario WHERE id = get_current_user_id();
$$ LANGUAGE SQL STABLE;

-- ============================================================================
-- ENABLE RLS EN TABLAS SENSIBLES
-- ============================================================================

-- Tabla ACTA: la más crítica
ALTER TABLE acta ENABLE ROW LEVEL SECURITY;

-- Tabla ACTA_DETALLE: desglose de votos (mismo acceso que acta)
ALTER TABLE acta_detalle ENABLE ROW LEVEL SECURITY;

-- Tabla EVIDENCIA: fotos (igual)
ALTER TABLE evidencia ENABLE ROW LEVEL SECURITY;

-- Tabla AUDIT_LOG: registro de acciones
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

-- Tabla NOTIFICACION: avisos
ALTER TABLE notificacion ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- POLICIES: ACTA
-- ============================================================================

-- Policy 1: OBSERVER solo puede VER actas de sus JRVs
CREATE POLICY rls_acta_observer_select ON acta
  FOR SELECT
  USING (
    -- Si el usuario es OBSERVER:
    (SELECT rol FROM usuario WHERE id = get_current_user_id()) = 'OBSERVER'
    AND jrv_id IN (
      -- Su jrv_id está en observer_jrv_asignacion
      SELECT jrv_id FROM observer_jrv_asignacion 
      WHERE observer_id = get_current_user_id()
    )
  )
  OR
  -- Si es COORDINATOR, ADMIN o MASTER: ve todo
  (SELECT rol FROM usuario WHERE id = get_current_user_id()) IN ('COORDINATOR', 'ADMIN', 'MASTER');

-- Policy 2: OBSERVER solo puede MODIFICAR (INSERT/UPDATE) actas en sus JRVs
CREATE POLICY rls_acta_observer_modify ON acta
  FOR INSERT
  WITH CHECK (
    (SELECT rol FROM usuario WHERE id = get_current_user_id()) = 'OBSERVER'
    AND jrv_id IN (
      SELECT jrv_id FROM observer_jrv_asignacion 
      WHERE observer_id = get_current_user_id()
    )
  )
  OR
  (SELECT rol FROM usuario WHERE id = get_current_user_id()) IN ('COORDINATOR', 'MASTER');

CREATE POLICY rls_acta_observer_update ON acta
  FOR UPDATE
  USING (
    (SELECT rol FROM usuario WHERE id = get_current_user_id()) = 'OBSERVER'
    AND jrv_id IN (
      SELECT jrv_id FROM observer_jrv_asignacion 
      WHERE observer_id = get_current_user_id()
    )
  )
  OR
  (SELECT rol FROM usuario WHERE id = get_current_user_id()) IN ('COORDINATOR', 'MASTER')
  OR
  -- Solo MASTER puede eliminar (casi nunca pasa)
  (SELECT rol FROM usuario WHERE id = get_current_user_id()) = 'MASTER';

-- ============================================================================
-- POLICIES: ACTA_DETALLE (heredar acceso de su acta)
-- ============================================================================

CREATE POLICY rls_acta_detalle_select ON acta_detalle
  FOR SELECT
  USING (
    acta_id IN (
      SELECT id FROM acta WHERE
        (
          (SELECT rol FROM usuario WHERE id = get_current_user_id()) = 'OBSERVER'
          AND jrv_id IN (
            SELECT jrv_id FROM observer_jrv_asignacion 
            WHERE observer_id = get_current_user_id()
          )
        )
        OR
        (SELECT rol FROM usuario WHERE id = get_current_user_id()) IN ('COORDINATOR', 'ADMIN', 'MASTER')
    )
  );

CREATE POLICY rls_acta_detalle_modify ON acta_detalle
  FOR INSERT
  WITH CHECK (
    acta_id IN (
      SELECT id FROM acta WHERE
        (
          (SELECT rol FROM usuario WHERE id = get_current_user_id()) = 'OBSERVER'
          AND jrv_id IN (
            SELECT jrv_id FROM observer_jrv_asignacion 
            WHERE observer_id = get_current_user_id()
          )
        )
        OR
        (SELECT rol FROM usuario WHERE id = get_current_user_id()) IN ('COORDINATOR', 'MASTER')
    )
  );

-- ============================================================================
-- POLICIES: EVIDENCIA (fotos)
-- ============================================================================

CREATE POLICY rls_evidencia_select ON evidencia
  FOR SELECT
  USING (
    (SELECT rol FROM usuario WHERE id = get_current_user_id()) IN ('COORDINATOR', 'ADMIN', 'MASTER')
    OR
    -- OBSERVER ve fotos de su JRVs
    (
      (SELECT rol FROM usuario WHERE id = get_current_user_id()) = 'OBSERVER'
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
    AND (SELECT rol FROM usuario WHERE id = get_current_user_id()) = 'OBSERVER'
  );

-- ============================================================================
-- POLICIES: AUDIT_LOG (lectura restringida)
-- ============================================================================

CREATE POLICY rls_audit_log_select ON audit_log
  FOR SELECT
  USING (
    -- Solo COORDINATOR, ADMIN, MASTER ven audit log
    (SELECT rol FROM usuario WHERE id = get_current_user_id()) IN ('COORDINATOR', 'ADMIN', 'MASTER')
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

CREATE POLICY rls_notificacion_mark_read ON notificacion
  FOR UPDATE
  USING (observer_id = get_current_user_id())
  WITH CHECK (observer_id = get_current_user_id());

-- ============================================================================
-- VERIFICACIÓN: Listar policies creadas
-- ============================================================================

-- SELECT tablename, policyname, permissive, roles, qual, with_check
-- FROM pg_policies
-- ORDER BY tablename;
```

### Paso 2 — Crear `RlsInterceptor.java`

Este archivo hace que ANTES de cada query, Spring ponga la session variable.

Ubicación: `backend/electoral-api/src/main/java/ec/twintailcs/electoral/shared/security/RlsInterceptor.java`

```java
package ec.twintailcs.electoral.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * Interceptor que establece la session variable de PostgreSQL app.current_user_id
 * antes de cada query. Necesario para que RLS funcione.
 *
 * El flujo:
 * 1. Usuario hace request con JWT
 * 2. Spring Security extrae el user_id del JWT
 * 3. Este filter lo establece en PostgreSQL: SET LOCAL app.current_user_id = '123'
 * 4. Todas las queries de este request usan esa variable
 * 5. RLS policies usan GET_CURRENT_USER_ID() para filtrar filas
 */
public class RlsInterceptor extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        // Obtener el usuario autenticado del JWT (vía Spring Security)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails details = (CustomUserDetails) auth.getPrincipal();
            Long userId = details.getUserId();
            
            // TODO: Aquí iría la lógica para ejecutar SET LOCAL app.current_user_id = '...'
            // en la conexión JDBC antes de las queries. Por ahora, dejo el esqueleto.
            
            // Opción 1: Si usas DataSource directo
            // Connection conn = DataSourceUtils.getConnection(dataSource);
            // conn.createStatement().execute("SET LOCAL app.current_user_id = '" + userId + "'");
            
            // Opción 2: Con JdbcTemplate (recomendado)
            // jdbcTemplate.execute("SET LOCAL app.current_user_id = '" + userId + "'");
        }
        filterChain.doFilter(request, response);
    }
}
```

Nota: Esta parte requiere integración con tu sistema actual de autenticación JWT. Déjalo como placeholder por ahora y lo implementarás en T-BE-XX de autenticación.

### Paso 3 — Ejecutar el script

```bash
# Conectar como admin (postgres o electoral_user con permisos)
psql -h localhost -U electoral_user -d electoral_db -f backend/electoral-api/src/main/resources/database/infra/sql/02-rls.sql
```

### Paso 4 — Verificar policies

```bash
psql -h localhost -U electoral_user -d electoral_db

-- Ver todas las policies
SELECT tablename, policyname FROM pg_policies ORDER BY tablename;

-- Ver policy específica de tabla
SELECT * FROM pg_policies WHERE tablename = 'acta';
```

---

## Cómo verificar que funciona

1. **Policies creadas:**
   ```bash
   psql -h localhost -U electoral_user -d electoral_db -c "SELECT COUNT(*) FROM pg_policies;"
   # Debería mostrar ~12+
   ```

2. **Función auxiliar disponible:**
   ```bash
   psql -h localhost -U electoral_user -d electoral_db -c "SELECT get_current_user_id();"
   # Error esperado: app.current_user_id no está seteado
   # Es normal; solo se setea desde el API
   ```

3. **RLS habilitado en tablas:**
   ```bash
   psql -h localhost -U electoral_user -d electoral_db -c "SELECT tablename FROM pg_tables WHERE rowsecurity = true;"
   # Debería mostrar: acta, acta_detalle, evidencia, audit_log, notificacion
   ```

---

## Errores comunes

**Error: `column "id" does not exist`**
Razón: RLS intenta acceder a columa que no existe en la tabla.
Solución: Revisa los nombres exactos de columnas en la tabla vs. en el SQL.

**Error: `function get_current_user_id() does not exist`**
Razón: No ejecutaste el script 02-rls.sql.
Solución: Ejecuta el script completo.

**RLS activa pero query devuelve filas que no debería**
Razón: La session variable `app.current_user_id` no se setea desde el API.
Solución: Completa RlsInterceptor (Paso 2) para hacer `SET LOCAL app.current_user_id = ...` antes de queries.

**Después de habilitar RLS, todas las queries son `ERROR: new row violates row-level security policy`**
Razón: La policy es muy restrictiva o la session variable no coincide.
Solución: Revisa que GET_CURRENT_USER_ID() retorna un ID válido y ese user tiene acceso según la policy.

---

## Checklist original (de infra.md)

- [ ] Habilitar RLS en tabla `acta`
- [ ] Policy OBSERVER: `jrv_id IN (SELECT jrv_id FROM observer_jrv_asignacion WHERE observer_id = current_user_id())`
- [ ] Policy COORDINATOR / ADMIN / MASTER: sin restricción de fila
- [ ] Función `current_user_id()` que lee claim del JWT (vía `app.current_user_id` setting)
- [ ] Verificar que API pasa `SET LOCAL app.current_user_id = ?` antes de queries
