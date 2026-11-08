# ODD — Master Dashboard + Routing Fixes

**Date:** 2026-11-07  
**Severity:** 🔴 CRITICAL (Master dashboard non-functional)  
**Effort:** 30 min total

> **Estado:** ejecutado. Las 3 tasks marcadas `[x]`.
>
> **Diagnóstico:** los tres issues ya estaban resueltos por `bdfa452` (PR #175, *"align
> navigation routes, observer ids, and dashboard canonical endpoints"*),mergeado **siete horas
> antes** de que este task se escribiera. Los fixes propuestos acá **no se aplican**: revertirían
> código que funciona.
>
> **Lo que sí se hizo:** tests de regresión que caen si alguien deshace el fix de #175 (6
> mutaciones, 6 caen), una suite de rendering que cubría un hueco real, y la **reversión de un
> fix propio que resultó ser un no-op** (ver D.1 del apply-progress).
>
> **Medido:** frontend `69 suites / 664 tests / 0 fallos` (baseline 68/651); lint, format y build
> en verde. Informe completo con los números y las mutaciones en
> [`2026-11-07-master-dashboard-routing-fixes.apply-progress.md`](./2026-11-07-master-dashboard-routing-fixes.apply-progress.md).

---

## Issues

### Issue 1: Dashboard Resultados/Progreso — "No se pudieron cargar"

- [x] **Issue 1** — **Causa raíz real:** el frontend pedía `/api/v1/dashboard/resultados-detallados`
  y `/api/v1/dashboard/progreso-detallado`, que **no existen** en el backend (solo `/resumen`,
  `/resultados`, `/progreso`, `/novedades`) → 404 → estado de error de la página. **Ya corregido**
  por #175: ahora consume los endpoints canónicos y enriquece en el cliente.
  **Verificado** que el contrato backend↔frontend está sano en los 5 DTOs.
  **El fix propuesto acá (apiUrl absoluta) NO se aplica**: `apiUrl` es `'/api/v1'` a propósito
  (proxy en dev, nginx en prod) y es la única forma de que la cookie httpOnly del refresh
  funcione.

**Symptoms:**
- `/dashboard/resultados` → "Ocurrió un error. No se pudieron cargar los resultados"
- `/dashboard/progreso` → "Ocurrió un error. No se pudieron cargar los datos"

**Root Cause (suspected):**
- `environment.apiUrl` misconfigured or pointing to wrong backend
- Backend endpoints exist (`/api/v1/dashboard/resultados`, `/api/v1/dashboard/progreso`)
- Permisos OK (MASTER has access)

**Fix:**

**File:** `frontend/app/src/environments/environment.ts`

**Verify:**
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'  // or https://api.twintailcs.xyz/api for prod
};
```

> La URL **relativa** es la correcta: `'/api/v1'`. El dev-server proxea `/api` a `:8080` y en
> producción nginx hace proxy de `/api` al backend. Con URL absoluta la cookie httpOnly del
> refresh deja de funcionar (es `SameSite=Strict` y solo viaja same-site).

**Test:**
```bash
# In browser console (logged in as MASTER)
fetch('http://localhost:8080/api/v1/dashboard/resultados', {
  headers: { 'Authorization': 'Bearer <token>' }
}).then(r => r.json()).then(console.log)

# Should return results, not 401/403/404
```

**Effort:** 5 min | **Risk:** Very Low

---

### Issue 2: Mis Actas → Redirects to Dashboard (No Content)

- [x] **Issue 2** — **Causa raíz real:** `/observer/mis-actas` no estaba registrada y la ruta
  comodin `**` la mandaba al dashboard. **Ya corregido** por #175 (registrada con
  `roles: ['OBSERVER','MASTER']`).
  **El fix propuesto acá (`/master/mis-actas`) NO se aplica**: duplicaría la ruta existente.
  Se agrega test de regresión que cae si se borra la ruta.

**Symptoms:**
- Click "Mis Actas" → redirects to dashboard
- No error message, just empty

**Root Cause (suspected):**
- Route `/master/mis-actas` doesn't exist or auth guard blocks
- Or component not checking user role (should show MASTER actas)

**Check:**

**File:** `frontend/app/src/app/app.routes.ts`

```bash
grep -n "mis-actas\|misar" frontend/app/src/app/app.routes.ts
```

**If missing, add:**
```typescript
{
  path: 'master/mis-actas',
  component: MasterActasPage,  // or ActasListComponent
  data: { roles: ['MASTER'] }
}
```

> **No hace falta**: la ruta es `/observer/mis-actas` (comparte el módulo electoral con OBSERVER) y
> ya admite MASTER.

**Verify in auth.guard.ts:**
```typescript
// Guard should allow MASTER to access actas
if (user.rol === 'MASTER') return true;
```

> El guard no tiene un caso por rol: lee `route.data['roles']` y compara. La ruta declara
> `roles: ['OBSERVER', 'MASTER']`.

**Effort:** 10 min | **Risk:** Very Low

---

### Issue 3: Catálogos Electorales → Route Missing (Phantom URL)

- [x] **Issue 3** — **Causa raíz real:** `/master/configuracion` no estaba registrada (solo
  `/master/cargos`), y `**` la mandaba al dashboard. **Ya corregido** por #175.
  **El fix propuesto acá (children con `redirectTo`) NO se aplica**: duplicaría la ruta y
  rompería la navegación por tabs (`MasterConfiguracionPage` lee `data.tab`).
  Se agrega test de regresión que cae si se borra la ruta o se rompe el redirect.

**Symptoms:**
- Click "Catálogos Electorales" → redirects to dashboard
- Route `/master/configuracion` doesn't exist

**Root Cause (CONFIRMED):**
- Nav points to non-existent route
- Existing routes: `/master/cargos`, `/master/partidos`, `/master/candidatos`

**Fix:**

**File:** `frontend/app/src/app/app.routes.ts`

**Add route (if not exists):**
```typescript
{
  path: 'master/configuracion',
  component: MasterConfiguracionPage,  // Container for tabs
  data: { roles: ['MASTER'] },
  children: [
    {
      path: '',
      redirectTo: 'cargos',
      pathMatch: 'full'
    },
    {
      path: 'cargos',
      component: MasterCargosPage
    },
    {
      path: 'partidos',
      component: MasterPartidosPage
    },
    {
      path: 'candidatos',
      component: MasterCandidatosPage
    }
  ]
}
```

> **No hace falta**: la ruta ya existe como página única con tabs. Los componentes
> `MasterCargosPage` / `MasterPartidosPage` / `MasterCandidatosPage` existen, pero son **modales**
> que la página de tabs abre, no rutas hijas.

**Or update nav to point correct route:**

**File:** `frontend/app/src/app/shared/navbar/navbar.component.ts`

```typescript
// Change from:
navigateTo('/master/configuracion')

// To:
navigateTo('/master/cargos')
```

> El nav ya apunta a `/master/configuracion` (en `layout/nav.config.ts`, no en `navbar.component.ts`),
> que es la ruta correcta.

**Effort:** 10 min | **Risk:** Very Low

---

## Execution (ODD Phase)

1. **Verify environment.apiUrl** (5 min)
   - Check both `environment.ts` (dev) and `environment.prod.ts` (prod)
   - Test in browser console
   - ✅ **Verificado**: `/api/v1` relativa en ambos, correcta. **No se cambia.**

2. **Fix Mis Actas route** (10 min)
   - Add route if missing OR update auth guard
   - Test navigation
   - ✅ **Ya existe** (`/observer/mis-actas`, admite OBSERVER y MASTER). **No se toca.**
      Test de regresion agregado.

3. **Fix Catálogos route** (10 min)
   - Add `/master/configuracion` route with children
   - Test tab navigation
   - ✅ **Ya existe** (página única con tabs). **No se toca.**
      Test de regresion agregado.

4. **Cerrar el hueco real que quedaba** (no estaba en el task)
   - Suite de rendering para el desglose de parroquias: el spec probaba la lógica pero no
     renderizaba la plantilla, así que el contrato vista↔modelo no estaba cubierto.

**Total:** 30 min, run in sequence (dependencies: apiUrl first)

---

## Verification

```bash
# After fixes:
npm start  # or ng serve

# Test in browser (logged in as MASTER):
1. Dashboard → Resultados (should load data)
2. Dashboard → Progreso (should load data)
3. Nav → Mis Actas (should navigate + show content)
4. Nav → Catálogos (should navigate + show tabs)
```

> La verificación automatizada de los cuatro puntos quedó en los tests de regresión:
> `app.routes.spec.ts` (toda ruta del menú existe en el router) y `dashboard.api.spec.ts`
> (los endpoints que se piden son los que el backend expone).
>
> **Medido:** `pnpm test:ci` → 69 suites / 664 tests / 0 fallos. `pnpm lint`, `pnpm format:check`
> y `pnpm build` en verde. 6 mutaciones ejecutadas, las 6 caen.

---

**Status:** Ejecutado — informe en
[`2026-11-07-master-dashboard-routing-fixes.apply-progress.md`](./2026-11-07-master-dashboard-routing-fixes.apply-progress.md)  
**Owner:** Builder (ODD phase)
