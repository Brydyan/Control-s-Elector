# Proposal: E2E Local Fix — Playwright (Pasos 1–4)

**ODD ID:** `e2e-local-fix-playwright`  
**Versión:** 1.0  
**Fecha:** 2026-11-13  
**Rama:** `brydyan/sc-510/odd-e2e-local-fix`  
**Responsable:** QA / Frontend

---

## 1. Intent (¿Por qué?)

Hoy los tests E2E de Playwright están **59 fallos de 66**. Causa raíz:

1. **Modales bloqueantes** (cookies + términos) no se despachan automáticamente
2. **Configuración duplicada** en conflicto (`playwright.config.ts` vs `e2e/playwright.config.ts`)
3. **Mocks desalineados** del backend real (`content` vs `data`, estructura falsa)
4. **Ruta crítica mockeada** (registro de acta) — viola invariante

**Objetivo:** Hacer que **E2E sea verde localmente** contra backend real, sin mocks en ruta crítica. Requisito previo para implementar CI.

---

## 2. Scope

### En Alcance
- ✅ `globalSetup.ts` con fixture de cookies + términos
- ✅ Consolidar `playwright.config.ts` (eliminar config muerta)
- ✅ Actualizar mocks en j1 para alinear con backend real
- ✅ Cambiar j1 a **no mockeado** (golpea backend real)
- ✅ Validar idempotencia (409 real)
- ✅ Hacer verde: j6 + j1 + j3 (al menos 10–15 tests)

### Fuera de Alcance
- ❌ CI workflow (Pasos 5–6, después de verde local)
- ❌ Refactoring de specs
- ❌ Agregar más tests
- ❌ Cambiar setup de Docker/Postgres

---

## 3. Acceptance Criteria

| AC | Descripción | Check |
|---|---|---|
| **AC-1** | `globalSetup.ts` existe, acepta términos + cookies | [ ] |
| **AC-2** | `playwright.config.ts` único, no conflictos | [ ] |
| **AC-3** | j6 (legal-terms) pasa 3/3 tests | [ ] |
| **AC-4** | j1 (registro) sin mocks, golpea backend real | [ ] |
| **AC-5** | j1 valida idempotencia: `409` real, no duplicado | [ ] |
| **AC-6** | j3 (cuadre-estricto) pasa 4/4 tests | [ ] |
| **AC-7** | Mocks en j4/j5 alineados con API real | [ ] |
| **AC-8** | Local: `pnpm run e2e:all` → ≥15 tests verdes | [ ] |
| **AC-9** | Cero regressions en tests unitarios (`pnpm test`) | [ ] |
| **AC-10** | Todos los commits en rama, sin force-push | [ ] |

---

## 4. Success Metrics

| KPI | Meta | Cómo medir |
|---|---|---|
| **Tests verdes** | ≥15/66 | `npx playwright test --reporter=json` |
| **Flakiness** | 0% (100 runs) | Correr localmente 3 veces |
| **Wall-clock local** | ≤6 min | `time npx playwright test` |
| **Backend calls reales** | ≥1 endpoint sin mock | Inspeccionar red en `--headed` |
| **Code coverage** | No baja | `pnpm test -- --coverage` |

---

## 5. Risks & Mitigations

| Risk | Severidad | Mitigación |
|---|---|---|
| Backend caído durante dev | 🔴 Alto | Documentar `mvn quarkus:dev` + healthcheck |
| DB sucio → 409 false-positive | 🟡 Medio | Usar `dropDatabaseOnStart: true` |
| Playwright timeout | 🟡 Medio | `timeout: 30000` global en config |
| Mocks old incompatibles con fixture | 🟡 Medio | Limpiar auth.json entre runs |

---

## 6. Timeline

| Fase | Tareas | Duración | Responsable |
|---|---|---|---|
| **P1** | globalSetup + fixture | 1–2h | QA |
| **P2** | Config único | 30–45 min | QA |
| **P3** | Mocks reales | 1h | QA |
| **P4** | j1 sin mocks | 1–2h | QA |
| **Total** | 3.5–5.5h | Menos de un día | QA |

---

## 7. Deliverables

1. **`frontend/app/globalSetup.ts`** — fixture de sesión
2. **`frontend/app/playwright.config.ts`** — config limpio
3. **`frontend/app/e2e/j1-observer-happy-path.spec.ts`** — actualizado sin mocks
4. **`apply-progress.md`** en esta carpeta — reporte de ejecución
5. **Commits** — 4–5 commits en rama, sin squash

---

## 8. Notes

- Backend Quarkus debe estar running: `mvn quarkus:dev`
- Frontend: `pnpm start` o `ng serve`
- Fixtures guardadas en `auth.json` (add to .gitignore si no está)
- Pasos 5–6 (CI) se implementan **después** de verde local
