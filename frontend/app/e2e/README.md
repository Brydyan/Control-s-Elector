# T-FE-10 Phase 2: E2E Tests (Playwright)

End-to-end tests for Coordinator Actas feature using Playwright.

## Setup

```bash
npm install --save-dev @playwright/test
npm install --save-dev @types/node
```

## Run Tests

```bash
# Run all journeys
npx playwright test

# Run specific journey
npx playwright test j1-load-filter-paginate.spec.ts

# Run in headed mode (see browser)
npx playwright test --headed

# Debug mode
npx playwright test --debug
```

## Journeys

### J1: Load / Filter / Paginate (1h)
- Load actas lista page
- Verify all estado tabs (including ANULADA - M1 fix)
- Filter by estado
- Search by term
- Navigate pages
- Verify data updates

**Location:** `journeys/j1-load-filter-paginate.spec.ts`

### J2: Approve Flow (1.5h)
- Navigate to ENVIADA acta
- Open Aprobar modal
- Confirm approval
- Verify toast message
- Verify estado → APROBADA

**Location:** `journeys/j2-approve-flow.spec.ts`

### J3: Dispute → Correct → Approve (2h)
- Dispute acta (fill motivo)
- Verify estado → EN_DISPUTA
- Correct acta (adjust votes, verify checksum)
- Verify estado → CORREGIDA
- Approve acta
- Verify estado → APROBADA

**Location:** `journeys/j3-dispute-correct-approve.spec.ts`

### J4: Anular (MASTER-only) (1h)
- Verify COORDINATOR cannot anular (button disabled)
- Login as MASTER
- Click Anular (fill motivo)
- Verify estado → ANULADA
- Verify acta in ANULADA tab

**Location:** `journeys/j4-anular-master-only.spec.ts`

### J5: 409 Conflict + Reload (0.5h)
- Attempt approve on locked acta
- Backend returns 409 Conflict
- Verify error message
- Verify auto-reload
- Verify retry works
- Verify can navigate away

**Location:** `journeys/j5-409-conflict-reload.spec.ts`

## Page Object Model

- `ActasListaPage` — List page actions (filter, search, pagination)
- `ActaDetallePage` — Detail page actions (open modals, fill forms)
- `AuthFixture` — Login/auth helpers

**Location:** `fixtures/pages.ts`

## Notes

- Tests assume app running on `http://localhost:4200`
- API endpoints mocked/stubbed in test setup
- Screenshots/videos on failure saved to `test-results/`
- HTML report: `playwright-report/`

## CI/CD Integration

```yaml
# GitHub Actions example
- name: Run E2E tests
  run: npx playwright test
```

## Effort

- **Total:** ~6 hours
- **Per Journey:** J1=1h, J2=1.5h, J3=2h, J4=1h, J5=0.5h
- **Setup:** ~0.5h

## Status

Phase 2b (E2E) — Ready for execution.

---

**Part of T-FE-10 Phase 2 Implementation**  
**Gap Coverage:** E2E journeys cover all 5 user workflows  
**Mutations:** Combined with Phase 2a mutations (M1, M6) = 90% explicit test coverage
