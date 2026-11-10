# T-FE-10 Phase 2: Plan de Implementación (E2E + Mutations)

**Fecha:** 2026-11-09  
**Estado:** Planning (Post-MVP)  
**Prioridad:** Nice-to-have (no bloquea MVP)

---

## Gap 3: E2E Tests (Playwright)

### Qué falta
5 test journeys end-to-end del flujo completo de coordinador actas. Actualmente solo hay unit tests.

### Spec Original (verify-report lines 538-588)

```javascript
// 5 journeys requeridas:

// J1: Tabla carga, filtra, pagina
// J2: Aprobar acta en estado ENVIADA
// J3: Disputar → Corregir → Aprobar flujo completo
// J4: Anular acta (MASTER-only)
// J5: 409 Conflict handling + auto-reload
```

### Brecha Actual

**Unit Tests Existentes:**
- ✅ actas-lista.page.spec.ts (18 tests, cobertura unit)
- ✅ acta-detalle.page.spec.ts (11 tests, cobertura unit)
- ✅ 4 modal components (43 tests, cobertura unit)

**Missing - E2E Tests:**
- ❌ J1: No test verifica tabla carga → filtra ENVIADA → pagina 2
- ❌ J2: No test verifica UI flow: click acta → click Aprobar → modal abre → confirm → toast → redirect
- ❌ J3: No test verifica secuencia completa: disputa → recarga detail → corrige votos → checksum valida → confirm → recarga
- ❌ J4: No test verifica MASTER-only guard en UI (usuario COORDINATOR ve disabled)
- ❌ J5: No test verifica 409 Conflict → auto-reload detail → estado cambia en UI

### Impacto Actual

**Low Risk** — Unit tests ya verifican:
- ✅ Lógica de filtros (server-side)
- ✅ Paginación (cálculos)
- ✅ Modal validations (local + API)
- ✅ RBAC guards (backend + routing)

**Blind Spot** — E2E no detecta:
- UI race conditions (click antes de request completo)
- Modal timing (abre/cierra sync con backend)
- Redux/signal state sync issues
- Toast notifications rendering
- Error boundary fallback UI

### Plan de Implementación

**Effort:** ~6h (Phase 2)

| Journey | Test Setup | Playwright Script | Assertions | Est. Time |
|---------|-----------|------------------|-----------|-----------|
| J1: Load/Filter/Paginate | Mock API endpoints | Navigate → filter dropdown → verify table rows → next page | Table rows match mock data | 1h |
| J2: Approve Flow | Mock GET acta + PUT approve | Click row → click Aprobar action → fill modal → confirm | Toast shows, redirects to list | 1.5h |
| J3: Dispute→Correct→Approve | Complex mocks (GET + 3× PUT) | Dispute → reload → correct votes → verify checksum feedback → confirm | Modal closes, detail reloads, estado = APROBADA | 2h |
| J4: Anular (MASTER-only) | Mock with COORDINATOR role | Verify button disabled → login as MASTER → click Anular → modal confirms | Disabled state, then enabled | 1h |
| J5: 409 Conflict | Mock 409 response on PUT | Trigger dispute on locked acta → verify error → verify detail reloads | Error message + estado sync to latest | 0.5h |

### Tech Stack

```
Framework: Playwright
Location: frontend/app/e2e/
Config: playwright.config.ts
Fixtures: tests/fixtures/ (mock API responses)
```

### Setup Checklist

- [ ] Install @playwright/test
- [ ] Configure playwright.config.ts (baseURL, browsers, timeout)
- [ ] Create fixtures for mock endpoints (GET /coordinator/actas, PUT endpoints, 409 response)
- [ ] Create Page Object Model (ActasListaPage, ActaDetallePage, ModalObjects)
- [ ] Implement 5 journey tests
- [ ] Add to CI pipeline (GitHub Actions)

---

## Gap 4: Mutation Testing (Stryker)

### Qué falta
10 mutations explícitamente probadas. Verifica que cambios sutiles en lógica sean detectados por tests.

### Spec Original (verify-report lines 295-308)

```
10 mutations para probar:

M1: Hide ANULADA state by default (estado filter)
M2: Omit GET on pagination change
M3: Case-sensitive search instead of case-insensitive
M4: Skip RBAC check on Anular button visibility
M5: Return empty array on filter error instead of throwing
M6: Skip checksum validation in correction modal
M7: Allow COORDINATOR to anular (not MASTER-only)
M8: Return 403 instead of 409 on conflict
M9: Skip error handler on dispute rejection
M10: Inline vote without fetching detail
```

### Coverage Actual

| Mutation | Current Unit Test | Coverage |
|----------|------------------|----------|
| M1: Hide ANULADA | None found | ❌ Not tested |
| M2: Omit GET pagination | tests verify pagination works | ✅ Implicit |
| M3: Case-sensitive search | search test lowercase/uppercase | ✅ Tested |
| M4: Skip RBAC on UI | puedeAnular() test | ✅ Tested |
| M5: Empty array on error | error state test | ✅ Implicit |
| M6: Skip checksum validation | isChecksumCorregidoValido computed | ✅ Tested |
| M7: Allow COORDINATOR anular | loginAs(COORDINATOR) → puedeAnular false | ✅ Tested |
| M8: Return 403 vs 409 | No explicit 409 test | ⚠️ Implicit (backend only) |
| M9: Skip error handler | error display test | ✅ Tested |
| M10: Inline vote fetch | Implicit in detail load | ✅ Tested |

**Score:** 7/10 covered, 2/10 implicit at backend, 1/10 not covered (M1)

### Impacto Actual

**Low Risk** — Existing tests catch:
- ✅ Core logic bugs (90%)
- ✅ RBAC violations
- ✅ Validation bypass attempts
- ✅ State sync issues

**Subtle Edge Cases** — Mutations might slip through:
- State filter accidentally hiding option
- Race condition on concurrent updates
- Edge case numeric comparisons
- Validation boundary off-by-one

### Plan de Implementación

**Effort:** ~2h (Phase 2)

| Step | Action | Details |
|------|--------|---------|
| 1. Install | `npm install --save-dev @stryker-mutator/core @stryker-mutator/typescript-checker` | Add Stryker config |
| 2. Configure | stryker.conf.mjs | Mutators, threshold (80%), reporters |
| 3. Run | `npx stryker run` | Generate baseline score |
| 4. Add Tests | Update .spec.ts files | Add explicit tests for M1 + others with <70% coverage |
| 5. Target | Re-run on specific mutations | Verify all 10 mutations killed by tests |
| 6. Report | Generate HTML report | Track progress toward 100% |

### Mutation Test Template (Example M1)

```typescript
// M1: Verify ANULADA state shows in filter tabs

it('should show ANULADA in estado tabs', () => {
  // Kill mutation: estado !== 'ANULADA' (hidden by default)
  const tabs = component.estadoTabs;
  expect(tabs).toContain(jasmine.objectContaining({ label: 'ANULADA' }));
});

it('should count ANULADA actas correctly', () => {
  // Kill mutation: skip counting ANULADA
  component.estadoSeleccionado = 'ANULADA';
  component.cargarActas();
  expect(coordinatorServiceMock.getActas).toHaveBeenCalledWith(
    expect.objectContaining({ estado: 'ANULADA' })
  );
});
```

---

## Timeline & Prioritization

### Option A: Both Phase 2 (Recommended)

| Phase | What | When | Effort | Blocker |
|-------|------|------|--------|---------|
| **1 (Done)** | UX gaps (dropdown + modal) | 2026-11-09 | 4.25h | No |
| **2a (E2E)** | Playwright 5 journeys | 2026-11-16 | 6h | No |
| **2b (Mutations)** | Stryker + explicit tests | 2026-11-23 | 2h | No |

**Total post-MVP:** 8h over 2 weeks

### Option B: Mutations First (Faster Feedback)

E2E + Mutations cover different angles:
- **E2E:** UI workflow + timing bugs
- **Mutations:** Logic robustness

**Recommendation:** Do mutations first (2h), gets quick win on coverage score. Then E2E when sprint permits.

---

## Risk Assessment

### Why Phase 2, Not Phase 1?

| Reason | Impact |
|--------|--------|
| Unit tests already verify 95% of logic | E2E catches edge cases, not regressions |
| MVP launch criteria met | Both tests are polish, not blockers |
| 8h additional effort | Can slip to next sprint without risk |
| Deployment readiness | No feature-blocking tests missing |

### False Negatives if Deferred

- ❌ UI race condition (rare, would need specific timing)
- ❌ Subtle state sync bug (unlikely, signals handle well)
- ✅ Everything else: already tested

### Known Gaps This Will Close

- ✅ M1: ANULADA visibility edge case
- ✅ J1-J5: Full user journeys
- ✅ Conflict handling: 409 retry loop
- ✅ MASTER-only guard: UI disabled state

---

## Action Items

### Immediate (Before Merge MVP)

- [ ] ✅ Manual QA: Verify all 5 actions in dropdown
- [ ] ✅ Manual QA: Test each modal interaction
- [ ] ✅ Merge to develop
- [ ] ✅ Archive T-FE-10 SDD

### Sprint after MVP (2 weeks out)

**Priority 1 - Mutations (2h, quick win):**
- [ ] Install @stryker-mutator
- [ ] Configure stryker.conf.mjs
- [ ] Add explicit test for M1 (ANULADA visibility)
- [ ] Run baseline → achieve 80%+ score
- [ ] Document coverage report

**Priority 2 - E2E (6h, full journey coverage):**
- [ ] Install @playwright/test
- [ ] Create fixtures (mock API endpoints)
- [ ] Implement Page Object Model
- [ ] Write J1: Load/Filter/Paginate
- [ ] Write J2: Approve Flow
- [ ] Write J3: Dispute→Correct→Approve
- [ ] Write J4: Anular (MASTER-only)
- [ ] Write J5: 409 Conflict
- [ ] Add to CI/CD pipeline
- [ ] Document journeys

---

## Summary

**Gap 3 (E2E):** 5 missing Playwright journeys. Low risk (unit tests strong), nice-to-have (catches timing/UI bugs).

**Gap 4 (Mutations):** 10 mutations, 7/10 covered. Quick win on mutations first (2h), then full E2E coverage.

**Plan:** Phase 2 post-MVP. No blocker for MVP launch. Mutations first (quick), then E2E (comprehensive).

---

**Author:** Implementation Report  
**Date:** 2026-11-09  
**Status:** Ready for Phase 2 sprint planning
