# Tasks — E2E Local Fix (Pasos 1–4)

**ODD ID:** `e2e-local-fix-playwright`  
**Status:** Ready to implement  
**Ejecutar en orden:** P1 → P2 → P3 → P4

---

## ✅ Entrega Final

Al terminar cada task, marcar `[x]`. Todos los commits en rama `brydyan/sc-510/odd-e2e-local-fix`.

---

## Phase 1: globalSetup + Fixture (1–2h)

### T1.1: Crear `frontend/app/globalSetup.ts`

**Objetivo:** Fixture que acepta cookies + términos antes de cualquier test.

**Archivos a crear:**
- `frontend/app/globalSetup.ts`

**Contenido requerido:**
```typescript
// globalSetup.ts
// Ejecuta una sola vez antes de todos los tests
// - Navega a /
// - Acepta cookies banner (si existe)
// - Acepta términos legales (si existe)
// - Guarda auth token en auth.json
// - Otros tests heredan sesión
```

**Criterios de éxito:**
- [ ] Archivo existe en `frontend/app/globalSetup.ts`
- [ ] Script navega a `/` y espera página cargada
- [ ] Detecta + acepta cookies banner con selector apropiado
- [ ] Detecta + acepta modal de términos legales
- [ ] Guarda sesión en `auth.json` (para reutilizar)
- [ ] Ejecutar: `npx playwright install` (si no existe)
- [ ] Ejecutar: `pnpm run e2e:smoke` → j6 tests: 3/3 ✅

**Commit message:**
```
feat(e2e): add globalSetup.ts with cookies & legal terms fixture

- Create global setup to accept cookies/terms modals once
- Save authenticated session in auth.json
- Reduce blocking modals from 59 tests to 7
```

---

### T1.2: Integrar globalSetup en playwright.config.ts

**Objetivo:** Configurar playwright para correr globalSetup antes de tests.

**Archivos a modificar:**
- `frontend/app/playwright.config.ts`

**Cambio requerido:**
```typescript
// playwright.config.ts
export default defineConfig({
  globalSetup: require.resolve('./globalSetup.ts'),  // ← AGREGAR
  fullyParallel: false,  // ← Primera run: lineal
  use: {
    storageState: 'auth.json',  // ← Reutilizar sesión
  },
  // resto del config igual
});
```

**Criterios de éxito:**
- [ ] Config incluye `globalSetup`
- [ ] Config incluye `storageState: 'auth.json'`
- [ ] Ejecutar: `pnpm run e2e:smoke` → sin timeout en modales
- [ ] Verificar en `--headed`: modales no reaparecen

**Commit message:**
```
feat(e2e): integrate globalSetup in playwright.config.ts

- Add globalSetup to config
- Enable storageState persistence
- Modals now auto-dismissed on first run
```

---

## Phase 2: Consolidar Config (30–45 min)

### T2.1: Identificar config duplicada

**Objetivo:** Encontrar y documentar conflictos entre `playwright.config.ts` (raíz) y `e2e/playwright.config.ts` (si existe).

**Tareas:**
- [ ] Verificar si existe `frontend/app/e2e/playwright.config.ts`
- [ ] Si existe: comparar con `frontend/app/playwright.config.ts`
- [ ] Documentar diferencias (timeouts, headless, reporters, etc.)
- [ ] Verificar cuál usan tests actualmente

**Criterios de éxito:**
- [ ] Listado de conflictos creado
- [ ] Decisión: merge o delete

---

### T2.2: Eliminar config duplicada

**Objetivo:** Dejar un único `playwright.config.ts` como fuente de verdad.

**Archivos a eliminar:**
- `frontend/app/e2e/playwright.config.ts` (si existe y es redundante)

**Archivos a actualizar:**
- `frontend/app/playwright.config.ts` (si necesita parámetros de e2e/config)

**Criterios de éxito:**
- [ ] Un único `playwright.config.ts` en `frontend/app/`
- [ ] Cero archivos `playwright.config.*` en `frontend/app/e2e/`
- [ ] Ejecutar: `npx playwright test --help` → sin conflictos
- [ ] Ejecutar: `pnpm run e2e:smoke` → aún pasa

**Commit message:**
```
fix(e2e): consolidate to single playwright.config.ts

- Remove e2e/playwright.config.ts (duplicate)
- Keep root config as single source of truth
- No behavior change
```

---

## Phase 3: Mocks Alineados (1h)

### T3.1: Auditar respuestas mock en j1

**Objetivo:** Verificar que mocks devuelven estructura correcta (`data: []` no `content: []`).

**Archivos a revisar:**
- `frontend/app/e2e/journeys/j1-observer-happy-path.spec.ts`

**Tareas:**
- [ ] Buscar todos `page.route()` handlers en j1
- [ ] Para cada mock: verificar que respuesta tenga estructura correcta
- [ ] Comparar con API real (`backend/electoral-api-quarkus`): ¿endpoint devuelve `PagedResponse` o `List`?

**Respuestas esperadas (backend real):**
- `/observer/mis-actas` → `PagedResponse` con `{data: [], meta: {}}`
- `/configuracion/cargos` → `List[]` directo
- `/observer/jrvs` → `List[]` directo

**Criterios de éxito:**
- [ ] Todos mocks alineados con backend real
- [ ] NPE cero al ejecutar j1 spec (mocks sin nulear)
- [ ] Ejecutar: `pnpm run e2e --grep 'j1'` → cero NPE

**Commit message:**
```
fix(e2e): align mock responses with backend structure

- j1 mocks now return PagedResponse {data, meta} for paginated endpoints
- Direct arrays for non-paginated endpoints
- Fixes 12 NullPointerException errors
```

---

### T3.2: Verificar j4/j5 mocks

**Objetivo:** Asegurar que mocks en j4 (disputas) y j5 (monitoreo) también alineados.

**Archivos a revisar:**
- `frontend/app/e2e/journeys/j4-disputa-correccion.spec.ts` (si existe)
- `frontend/app/e2e/journeys/j5-monitoreo-en-vivo.spec.ts` (si existe)

**Tareas:**
- [ ] Revisar respuestas mock
- [ ] Actualizar a estructura correcta
- [ ] Ejecutar: `pnpm run e2e --grep 'j4|j5'` → sin NPE

**Criterios de éxito:**
- [ ] j4/j5 mocks estructuralmente correctos
- [ ] Cero NPE en j4/j5 tests

**Commit message:**
```
fix(e2e): align j4/j5 mock responses with API contract

- Ensure PagedResponse format for paginated endpoints
- No functional change, structural alignment only
```

---

## Phase 4: j1 Sin Mocks (1–2h)

### T4.1: Remover intercepts del path crítico (j1)

**Objetivo:** j1 golpea backend real (sin mocks) para validar idempotencia HTTP real.

**Archivos a modificar:**
- `frontend/app/e2e/journeys/j1-observer-happy-path.spec.ts`

**Tareas:**
- [ ] Remover todos `page.route()` handlers en j1 spec
- [ ] Dejar solo mocks globales (si existen: error handlers, auth)
- [ ] Agregar comentario: `// @tag('guardian') @tag('no-intercept')`

**Criterios de éxito:**
- [ ] j1 spec sin `page.route()` del flujo de registro
- [ ] Backend debe estar corriendo: `mvn quarkus:dev` (en otra terminal)
- [ ] Ejecutar: `pnpm run e2e --grep 'j1'` → tests quedan en PASS o FAIL reales (no mock)

**Commit message:**
```
feat(e2e): remove mocks from j1 registration critical path

- j1 now hits backend real (no intercepts)
- Validates idempotence with real 409 Conflict responses
- Requires backend running: mvn quarkus:dev
```

---

### T4.2: Agregar validación 409 (idempotence)

**Objetivo:** Uno de los tests en j1 debe validar que enviar mismo acta dos veces devuelve 409 (no duplica).

**Archivos a modificar:**
- `frontend/app/e2e/journeys/j1-observer-happy-path.spec.ts`

**Cambio requerido:**
```typescript
test('Registro completo: BORRADOR → ENVIADA, checksum correcto', async ({ page }) => {
  // 1. Primer registro: OK (200 o 201)
  await page.goto('/observer/dashboard');
  await page.fill('#form-jrv', 'JRV-001');
  // ... llenar formulario ...
  await page.click('button:has-text("Enviar")');
  await page.waitForResponse(res => res.url().includes('/observer/actas') && res.status() === 201);
  
  // 2. Segundo registro (mismo checksum): 409 Conflict (no crea duplicado)
  await page.fill('#form-jrv', 'JRV-001');
  // ... mismo formulario ...
  await page.click('button:has-text("Enviar")');
  const res409 = await page.waitForResponse(res => res.url().includes('/observer/actas') && res.status() === 409);
  expect(res409.status()).toBe(409);  // ← Idempotencia validada
});
```

**Criterios de éxito:**
- [ ] Test existe: "Validar idempotencia: 409 Conflict en duplicado"
- [ ] Test ejecuta sin mock
- [ ] Ejecutar: `pnpm run e2e --grep 'j1.*409'` → PASS con 409 real del backend

**Commit message:**
```
test(e2e): add idempotency validation for duplicate registration

- j1 test validates 409 Conflict on duplicate checksum
- Ensures backend rejects duplicate actas correctly
- Hits real backend (no mock)
```

---

### T4.3: Validación local verde

**Objetivo:** Verificar que ≥15 tests verdes localmente (j6, j1, j3).

**Requisitos previos:**
- [ ] Backend corriendo: `mvn quarkus:dev`
- [ ] Frontend corriendo: `pnpm start` o `ng serve`
- [ ] auth.json creado (de T1.1)

**Tareas:**
- [ ] Ejecutar: `pnpm run e2e:smoke` (j6)
- [ ] Ejecutar: `pnpm run e2e --grep 'j1'` (j1)
- [ ] Ejecutar: `pnpm run e2e --grep 'j3'` (j3)
- [ ] Contar tests verdes totales
- [ ] Ejecutar: `pnpm test` (unit tests) → sin regressions
- [ ] Ejecutar: `pnpm run e2e:all` (si posible) → reportar total

**Criterios de éxito:**
- [ ] j6 (legal-terms): 3/3 ✅
- [ ] j1 (observer-happy-path): ≥8/8 ✅
- [ ] j3 (cuadre-estricto): 4/4 ✅
- [ ] **Total ≥15 tests verdes localmente**
- [ ] Unit tests: 0 regressions
- [ ] Wall-clock: ≤6 min

**Evidencia requerida:**
- [ ] Screenshot de `npx playwright test --reporter=list` output
- [ ] O output de `pnpm run e2e:all`

---

### T4.4: Validar cero regressions (unit tests)

**Objetivo:** Asegurar que cambios E2E no rompieron tests unitarios.

**Tareas:**
- [ ] Ejecutar: `pnpm test` en `frontend/app/`
- [ ] Ejecutar: `rtk jest` (si existe jest config)
- [ ] Verificar: todas pruebas unitarias pasan

**Criterios de éxito:**
- [ ] Cero test failures en suite de unit tests
- [ ] Si hay warnings: documentar (no bloquea, pero registrar)

**Commit message:**
```
test(e2e): verify no regressions in unit tests

- All unit tests pass after E2E local fixes
- No behavioral changes to frontend logic
```

---

## 🏁 Fase Final: Entrega

### T5: Crear `apply-progress.md`

**Objetivo:** Reportar estado de todas tasks.

**Contenido requerido:**
```markdown
# Apply Progress — E2E Local Fix

**ODD ID:** `e2e-local-fix-playwright`  
**Rama:** `brydyan/sc-510/odd-e2e-local-fix`  
**Fecha:** [fecha completación]

## Tasks Completadas

- [x] T1.1: globalSetup.ts con fixture
- [x] T1.2: Integrar en config
- [x] T2.1: Identificar config duplicada
- [x] T2.2: Consolidar config único
- [x] T3.1: Auditar mocks j1
- [x] T3.2: Verificar j4/j5 mocks
- [x] T4.1: Remover intercepts j1
- [x] T4.2: Agregar validación 409
- [x] T4.3: Validación local verde
- [x] T4.4: Cero regressions

## Test Results

**Antes:** 59 failures / 66 tests  
**Después:** ≥15 passing / 66 tests

| Suite | Antes | Después | Estado |
|-------|-------|---------|--------|
| j6 legal-terms | 0/3 | 3/3 | ✅ |
| j1 registration | 0/8 | 8/8 | ✅ |
| j3 cuadre-estricto | 0/4 | 4/4 | ✅ |
| Unit tests | 150/150 ✅ | 150/150 ✅ | ✅ |

**Wall-clock:** 4.5 min  
**Flakiness:** 0% (3 runs)

## Commits

- `abc1234` feat(e2e): add globalSetup.ts with fixture
- `def5678` feat(e2e): integrate globalSetup in config
- `ghi9012` fix(e2e): consolidate to single playwright.config.ts
- `jkl3456` fix(e2e): align mock responses with backend
- `mno7890` feat(e2e): remove mocks from j1 critical path
- `pqr0123` test(e2e): add idempotency validation

## Next Steps

✅ **Local verde completado**  
🟡 **Pasos 5–6:** Design CI + Workflow (después de merge a develop)

---

**Signed:** Andy Alejandro  
**Merge ready:** Sí, all AC passed
```

**Criterios de éxito:**
- [ ] Archivo creado: `odd/e2e-local-fix-playwright/apply-progress.md`
- [ ] Lista todas tasks `[x]` completadas
- [ ] Reporta test counts antes/después
- [ ] Lista commits realizados
- [ ] Clearness para QA review

---

## 📋 Checklist Final

Antes de marcar esta ODD como **DONE**:

- [ ] Todos los tasks T1.1–T4.4 están `[x]`
- [ ] `apply-progress.md` creado y completo
- [ ] Rama limpia: `git status` = no uncommitted changes
- [ ] Todos commits tienen mensaje convencional (sin AI co-author)
- [ ] Tests locales: ≥15 verdes, 0 regressions
- [ ] Backend real validado (409 idempotencia)
- [ ] Ready para: `git push && gh pr create` a develop

---

**Siguiente:** Pasos 5–6 (CI Sharding) se implementan DESPUÉS de merge a develop.
