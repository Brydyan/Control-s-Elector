# ODD — Master Dashboard + Routing Fixes

**Date:** 2026-11-07  
**Severity:** 🔴 CRITICAL (Master dashboard non-functional)  
**Effort:** 30 min total

---

## Issues

### Issue 1: Dashboard Resultados/Progreso — "No se pudieron cargar"

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

**Verify in auth.guard.ts:**
```typescript
// Guard should allow MASTER to access actas
if (user.rol === 'MASTER') return true;
```

**Effort:** 10 min | **Risk:** Very Low

---

### Issue 3: Catálogos Electorales → Route Missing (Phantom URL)

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

**Or update nav to point correct route:**

**File:** `frontend/app/src/app/shared/navbar/navbar.component.ts`

```typescript
// Change from:
navigateTo('/master/configuracion')

// To:
navigateTo('/master/cargos')
```

**Effort:** 10 min | **Risk:** Very Low

---

## Execution (ODD Phase)

1. **Verify environment.apiUrl** (5 min)
   - Check both `environment.ts` (dev) and `environment.prod.ts` (prod)
   - Test in browser console

2. **Fix Mis Actas route** (10 min)
   - Add route if missing OR update auth guard
   - Test navigation

3. **Fix Catálogos route** (10 min)
   - Add `/master/configuracion` route with children
   - Test tab navigation

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

---

**Status:** Ready for execution  
**Owner:** Builder (ODD phase)
