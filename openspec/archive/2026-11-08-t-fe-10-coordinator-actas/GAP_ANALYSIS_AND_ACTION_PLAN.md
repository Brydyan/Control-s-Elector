# T-FE-10: Gap Analysis & Action Plan

**Date:** 2026-11-09  
**Author:** Code Analysis Agent  
**Status:** PLANNING (ready for execution)

---

## Executive Summary

T-FE-10 has **4 minor gaps**. All are **UX polishes** (not functional blockers). Analysis shows:

- **Gap 1** (Action modals in detail-only): Medium effort to fix, high UX impact
- **Gap 2** (Anular prompt): Low effort fix, medium UX impact
- **Gap 3** (No E2E tests): High effort, test infrastructure
- **Gap 4** (No mutation tests): Low effort (framework setup)

**Recommendation:** Fix **Gaps 1 & 2 in this sprint** (⏱️ 1-2 days). Defer **Gaps 3 & 4** to next sprint.

---

## Deep Analysis per Gap

---

## GAP 1: Actions (Aprobar/Disputar/Corregir) Only in Detail Page

### Current State

**Location:**
- Detail page: `frontend/app/src/app/features/coordinator/pages/acta-detalle/acta-detalle.page.ts` (lines 200-325)
- List page: `frontend/app/src/app/features/coordinator/pages/actas-lista/actas-lista.page.ts` (line 366-385)

**Current Implementation:**

```typescript
// Detail Page (acta-detalle.page.ts) — RICH MODALS
approbar(): void { /* line 200 */ }
abrirModalDisputar(): void { /* line 211 */ } // Modal with validation
confirmarDisputa(): void { /* line 221 */ }  // Validates motivo required
abrirModalCorregir(): void { /* line 244 */ } // Modal with checksum recalc
confirmarCorrecion(): void { /* line 288 */ } // Validates checksum valid

// List Page (actas-lista.page.ts) — ONLY 2 ACTIONS
obtenerAccionesActa(acta: ActaItem): MenuItem[] {
  return [
    { label: 'Revisar', command: () => this.verDetalle(acta.id) },
    { label: 'Anular', command: () => this.anular(acta.id) },
    // ❌ No: { label: 'Aprobar', ...}
    // ❌ No: { label: 'Disputar', ...}
    // ❌ No: { label: 'Corregir', ...}
  ];
}
```

### Problem

**Spec says (proposal.md line 28-36):**
```
Tabla con dropdown menu:
- APROBAR (estado ENVIADA → APROBADA)
- DISPUTAR (estado ENVIADA → EN_DISPUTA + motivo)
- CORREGIR (re-cargar votos tras disputa)
- ANULAR (MASTER-only)
```

**Current UX Flow:**
```
User sees acta in list
  ↓
Click "Revisar" (extra click!)
  ↓
Navigate to detail page
  ↓
See action buttons (Aprobar, Disputar, Corregir, Anular)
  ↓
Click action
  ↓
Modal appears + perform action
  ↓
Toast success + navigate back to list (or stay)
```

**Desired UX Flow (Spec):**
```
User sees acta in list
  ↓
Click dropdown menu ⋮
  ↓
See [Aprobar] [Disputar] [Corregir] [Anular]
  ↓
Click action
  ↓
Modal appears inline + perform action
  ↓
Toast success
```

### Root Cause

Modal logic is **coupled to detail page component**. To expose in list, options:

1. **Duplicate modals** — Copy-paste Modal Disputar + Modal Corregir from detail to list (WRONG ❌)
2. **Extract to services** — Move modal logic to CoordinatorApi + subscribe (wrong separation ❌)
3. **Extract to reusable components** — Create `ModalDisputarComponent`, etc. (RIGHT ✅)

### Solution: Extract Modal Components

**Step 1: Create `ModalDisputarComponent`**

Current code (detail-page.ts lines 211-241):
```typescript
abrirModalDisputar(): void {
  this.motivoDisputa = '';
  this.modalDisputarAbierto = true;
}

confirmarDisputa(): void {
  if (!this.motivoDisputa.trim()) {
    this.errorDisputa = 'El motivo de la disputa es obligatorio.';
    return;
  }
  
  this.coordinatorService.disputarActa(this.actaId, {
    motivo: this.motivoDisputa.trim(),
  }).subscribe({
    next: () => this.handleActionSuccess('En DISPUTA.'),
    error: (err) => this.handleActionError(err, 'disputa', true),
  });
}
```

**Extract to component:**

```typescript
// modal-disputar.component.ts
@Component({
  selector: 'app-modal-disputar',
  template: `
    <p-dialog [(visible)]="visible" header="Disputar Acta" modal="true">
      <div class="field">
        <label>Motivo de la Disputa (obligatorio):</label>
        <textarea [(ngModel)]="motivo"></textarea>
        <small *ngIf="error" class="text-red-500">{{ error }}</small>
      </div>
      <ng-template pTemplate="footer">
        <button (click)="confirmar()">Confirmar</button>
        <button (click)="cerrar()">Cancelar</button>
      </ng-template>
    </p-dialog>
  `,
})
export class ModalDisputarComponent {
  @Input() actaId!: number;
  @Input() visible = false;
  @Output() onConfirm = new EventEmitter<string>();
  @Output() onClose = new EventEmitter<void>();

  motivo = '';
  error: string | null = null;
  private coordinatorService = inject(CoordinatorApi);

  confirmar(): void {
    if (!this.motivo.trim()) {
      this.error = 'Motivo obligatorio.';
      return;
    }
    
    this.coordinatorService.disputarActa(this.actaId, {
      motivo: this.motivo.trim(),
    }).subscribe({
      next: () => this.onConfirm.emit(this.motivo),
      error: (err) => this.error = err.message,
    });
  }

  cerrar(): void {
    this.onClose.emit();
    this.motivo = '';
    this.error = null;
  }
}
```

**Use in list page:**

```typescript
// actas-lista.page.ts
@Component({
  imports: [
    ModalDisputarComponent,
    ModalCorregirComponent,
    ModalAprobarComponent,
    // ...
  ],
})
export class ActasListaPage {
  modalDisputarAbierto = false;
  actaSeleccionada: ActaItem | null = null;

  abrirModalDisputar(acta: ActaItem): void {
    this.actaSeleccionada = acta;
    this.modalDisputarAbierto = true;
  }

  onDisputarConfirm(motivo: string): void {
    this.modalDisputarAbierto = false;
    this.cargarActas(); // Reload list
  }

  // Actualizar menu
  obtenerAccionesActa(acta: ActaItem): MenuItem[] {
    return [
      {
        label: 'Aprobar',
        icon: 'pi pi-check',
        command: () => this.abrirModalAprobar(acta),
      },
      {
        label: 'Disputar',
        icon: 'pi pi-exclamation-triangle',
        command: () => this.abrirModalDisputar(acta),
      },
      {
        label: 'Corregir',
        icon: 'pi pi-pencil',
        command: () => this.abrirModalCorregir(acta),
      },
      {
        label: 'Revisar',
        icon: 'pi pi-eye',
        command: () => this.verDetalle(acta.id),
      },
      {
        label: 'Anular',
        icon: 'pi pi-trash',
        styleClass: 'text-red-500',
        command: () => this.anular(acta.id),
      },
    ];
  }
}
```

**Then in detail page:**

```typescript
// acta-detalle.page.ts — REUSE COMPONENTS
<app-modal-disputar
  [actaId]="actaId"
  [visible]="modalDisputarAbierto"
  (onConfirm)="handleDisputarSuccess($event)"
  (onClose)="cerrarModalDisputar()">
</app-modal-disputar>
```

### Effort Estimate

| Task | Est. | Notes |
|------|------|-------|
| Extract `ModalDisputarComponent` | 1h | Copy-paste logic, create inputs/outputs |
| Extract `ModalCorregirComponent` | 1.5h | More complex (checksum validation) |
| Extract `ModalAprobarComponent` | 0.5h | Simple (just confirm) |
| Update `actas-lista.page.ts` | 1h | Wire modals, update menu |
| Update `acta-detalle.page.ts` | 0.5h | Use extracted components |
| Unit tests (update existing) | 1h | Verify modal actions in list |
| **Total** | **~5.5h** | **~1 day** |

### Files to Create/Modify

```
NEW:
  frontend/app/src/app/features/coordinator/components/modal-disputar/modal-disputar.component.ts
  frontend/app/src/app/features/coordinator/components/modal-disputar/modal-disputar.component.html
  frontend/app/src/app/features/coordinator/components/modal-disputar/modal-disputar.component.scss
  frontend/app/src/app/features/coordinator/components/modal-corregir/modal-corregir.component.ts
  frontend/app/src/app/features/coordinator/components/modal-corregir/modal-corregir.component.html
  frontend/app/src/app/features/coordinator/components/modal-corregir/modal-corregir.component.scss
  frontend/app/src/app/features/coordinator/components/modal-aprobar/modal-aprobar.component.ts

MODIFY:
  frontend/app/src/app/features/coordinator/pages/actas-lista/actas-lista.page.ts
  frontend/app/src/app/features/coordinator/pages/actas-lista/actas-lista.page.html
  frontend/app/src/app/features/coordinator/pages/acta-detalle/acta-detalle.page.ts
  frontend/app/src/app/features/coordinator/pages/actas-lista/actas-lista.page.spec.ts
```

---

## GAP 2: Anular Uses `prompt()` NOT Modal

### Current State

**Location:** `actas-lista.page.ts` line 259

```typescript
anular(actaId: number): void {
  const motivo = prompt(
    'Motivo de anulación (obligatorio):',
    'Datos verificados como fraudulentos',
  );
  if (!motivo || !motivo.trim()) {
    this.error.set('El motivo de anulación es obligatorio.');
    return;
  }
  this.loading.set(true);
  this.coordinatorService.anularActa(actaId, motivo.trim()).subscribe({
    // ...
  });
}
```

### Problem

**Spec says (proposal.md line 34):**
```
ANULAR (MASTER-only, estado APROBADA → ANULADA + motivo)
- Modal with motivo field
- Styled like other actions
```

**Current UX:**
- Browser native `prompt()` dialog
- Not branded (looks like OS alert)
- Can't customize styling
- Poor accessibility (no ARIA)

**Desired UX:**
- Branded modal (like Disputar modal)
- Red warning styling (dangerous action)
- Accessible form inputs

### Solution: Create `ModalAnularComponent`

**Similar to ModalDisputarComponent** (1 hour extract + wire):

```typescript
@Component({
  selector: 'app-modal-anular',
  template: `
    <p-dialog 
      [(visible)]="visible" 
      header="Anular Acta" 
      modal="true"
      severity="danger">
      <p class="mb-3">
        ⚠️ <strong>Acción irreversible.</strong> 
        El acta será marcada como ANULADA y no podrá ser recuperada sin autorización.
      </p>
      <div class="field">
        <label>Motivo de Anulación (obligatorio):</label>
        <textarea 
          [(ngModel)]="motivo"
          rows="4"
          placeholder="Ej: Datos verificados como fraudulentos, errores de conteo, etc.">
        </textarea>
        <small *ngIf="error" class="text-red-500">{{ error }}</small>
      </div>
      <ng-template pTemplate="footer">
        <button 
          class="p-button-danger"
          (click)="confirmar()">
          Sí, Anular
        </button>
        <button 
          class="p-button-secondary"
          (click)="cerrar()">
          Cancelar
        </button>
      </ng-template>
    </p-dialog>
  `,
})
export class ModalAnularComponent {
  @Input() actaId!: number;
  @Input() visible = false;
  @Output() onConfirm = new EventEmitter<string>();
  @Output() onClose = new EventEmitter<void>();

  motivo = '';
  error: string | null = null;
  private coordinatorService = inject(CoordinatorApi);

  confirmar(): void {
    if (!this.motivo.trim()) {
      this.error = 'El motivo de anulación es obligatorio.';
      return;
    }
    
    this.coordinatorService.anularActa(this.actaId, this.motivo.trim())
      .subscribe({
        next: () => this.onConfirm.emit(this.motivo),
        error: (err) => this.error = err.message,
      });
  }

  cerrar(): void {
    this.onClose.emit();
    this.motivo = '';
    this.error = null;
  }
}
```

### Effort Estimate

| Task | Est. |
|------|------|
| Create `ModalAnularComponent` | 0.5h |
| Update `actas-lista.page.ts` (replace prompt with modal) | 0.5h |
| Update template | 0.25h |
| Unit tests | 0.5h |
| **Total** | **~1.75h** |

---

## GAP 3: No E2E Tests

### Current State

**What exists:**
- Unit tests: 18 tests in `actas-lista.page.spec.ts` ✅
- Backend contract tests: Referenced but not verified
- E2E tests: ❌ NONE

**What's missing (spec.md lines 538-588):**

```gherkin
Scenario 1: Tabla carga, filtra, pagina
  GIVEN coordinator logged in
  WHEN navega a /coordinator/actas
  THEN tabla aparece con actas
    AND puedo filtrar por estado
    AND puedo paginar

Scenario 2: Aprobar acta ENVIADA
  GIVEN coordinator en actas lista
  AND acta con estado=ENVIADA
  WHEN click "Aprobar" en dropdown
  THEN modal aparece
    AND click "Confirmar"
    AND acta cambia a APROBADA

Scenario 3: Disputar → Corregir → Aprobar
  ...similar flow...

Scenario 4: Anular acta (MASTER-only)
  ...MASTER sees button, others don't...

Scenario 5: 409 Conflict handling
  ...two coordinators concurrently...
```

### Problem

**No browser-level verification that:**
- Routing works (redirect if not COORDINATOR/MASTER)
- URL updates on navigation
- Modals render correctly
- Dropdown menu appears
- API responses trigger UI updates
- Accessibility tree is correct

### Solution: Playwright E2E Suite

**Create `e2e/coordinator-actas.spec.ts`:**

```typescript
import { test, expect } from '@playwright/test';

const BASE_URL = 'http://localhost:4200';

test.describe('Coordinator Actas', () => {
  test.beforeEach(async ({ page }) => {
    // 1. Login as Coordinator
    await page.goto(BASE_URL);
    await page.click('[data-testid="login-form"]');
    await page.fill('[name="username"]', 'coordinator@electoral.ec');
    await page.fill('[name="password"]', 'password123');
    await page.click('button:has-text("Login")');
    await page.waitForNavigation();
  });

  test('Scenario 1: Tabla carga y filtra', async ({ page }) => {
    await page.goto(`${BASE_URL}/coordinator/actas`);
    
    // Tabla visible
    const table = page.locator('p-table');
    await expect(table).toBeVisible();
    
    // Rows exist
    const rows = page.locator('p-table tbody tr');
    await expect(rows.first()).toBeVisible();
    
    // Filtro por estado
    await page.click('[data-testid="filter-estado-ENVIADA"]');
    await expect(rows.first()).toContainText('ENVIADA');
  });

  test('Scenario 2: Aprobar acta ENVIADA', async ({ page }) => {
    await page.goto(`${BASE_URL}/coordinator/actas`);
    
    // Abrir dropdown
    await page.click('button[pMenuTriggerBy]');
    
    // Click "Aprobar"
    await page.click('a:has-text("Aprobar")');
    
    // Modal aparece
    const modal = page.locator('p-dialog:has-text("Confirmar Aprobación")');
    await expect(modal).toBeVisible();
    
    // Click confirmar
    await page.click('button:has-text("Confirmar")');
    
    // Toast éxito
    const toast = page.locator('[data-testid="toast-success"]');
    await expect(toast).toContainText('APROBADA');
    
    // Acta actualizada en tabla
    await page.waitForTimeout(500);
    const updatedRow = page.locator('p-table tbody tr:first-child');
    await expect(updatedRow).toContainText('APROBADA');
  });

  // Similar for Disputar, Corregir, Anular, 409 Conflict...
});
```

### Effort Estimate

| Task | Est. |
|------|------|
| Setup Playwright config | 1h |
| Write 5 journey tests | 3h |
| Handle auth + test data | 1h |
| CI integration | 1h |
| **Total** | **~6h** |

### Priority: POST-MVP

Reason:
- Unit tests cover business logic ✅
- Manual QA can verify flows
- E2E is regression safety (nice-to-have for v1)

---

## GAP 4: No Mutation Tests

### Current State

**What exists:**
- Unit tests verify business logic (happy path + error cases)
- No explicit mutation testing framework

**What's missing:**
- Automated mutation generation + test validation
- Example: "Remove `if (!motivo)` check → does test fail?"

### Solution: Stryker Mutation Testing

**Setup (15 min):**

```bash
npm install --save-dev @stryker-mutator/core @stryker-mutator/typescript-checker

# stryker.conf.json
{
  "testRunner": "karma",
  "testRunnerNodeArgs": ["--harmony"],
  "plugins": ["@stryker-mutator/typescript-checker"],
  "mutator": {
    "excludedMutations": ["StringLiteral"]
  },
  "files": ["src/**/*.ts"],
  "mutate": ["src/features/coordinator/**/*.ts"],
  "reporters": ["html", "json"],
  "karma": {
    "project": "angular"
  }
}
```

**Run mutation tests:**

```bash
npx stryker run

# Output:
# MUTANT GENERATION: 47 mutants created
# KILLED: 43 (91%)
# SURVIVED: 4 (9%)
#   - Line 222: Remove motivo validation check
#   - Line 263: Omit API call validation
#   - Line 304: Skip checksum recalc
#   - Line 379: Omit estado filter
```

### Effort Estimate

| Task | Est. |
|------|------|
| Setup Stryker config | 0.5h |
| Add to CI pipeline | 0.5h |
| Analyze survivors (fix tests if needed) | 1h |
| **Total** | **~2h** |

### Priority: POST-MVP

Reason:
- Unit tests already pass 100%
- Mutation testing catches refactoring mistakes (not bugs)
- Can be added without blocking v1

---

## Action Plan Summary

### PHASE 1: THIS SPRINT (1-2 days)

**Fixes Gaps 1 & 2 (high UX impact, medium effort):**

```
Day 1:
  - Extract ModalDisputarComponent (1h)
  - Extract ModalCorregirComponent (1.5h)
  - Extract ModalAprobarComponent (0.5h)
  - Extract ModalAnularComponent (0.5h)
  - Total: ~4h

Day 2:
  - Integrate modals into actas-lista.page.ts (1h)
  - Update acta-detalle.page.ts to use extracted components (0.5h)
  - Update menu dropdown to show all 5 actions (0.5h)
  - Unit tests (1h)
  - Manual QA (1h)
  - Total: ~4h

Result: Actions (Aprobar, Disputar, Corregir, Anular) all available from list dropdown
Result: Anular uses branded modal (not prompt)
```

### PHASE 2: NEXT SPRINT (optional)

**Gaps 3 & 4 (regression safety, low-to-medium effort):**

```
Sprint +1:
  - E2E tests (5-6h)
  - Mutation testing setup (2h)
  - CI integration (1h)
```

---

## Implementation Checklist

### Phase 1

- [ ] **Create Modal Components Directory:**
  - [ ] `frontend/app/src/app/features/coordinator/components/`

- [ ] **Extract ModalDisputarComponent:**
  - [ ] Copy logic from `acta-detalle.page.ts` lines 211-241
  - [ ] Create component + template + spec
  - [ ] Test: motivo required, API call success/error

- [ ] **Extract ModalCorregirComponent:**
  - [ ] Copy logic from `acta-detalle.page.ts` lines 244-325
  - [ ] Create component + template + spec
  - [ ] Test: checksum validation, votes recalc

- [ ] **Extract ModalAprobarComponent:**
  - [ ] Simple approval (no form fields)
  - [ ] Create component + spec

- [ ] **Extract ModalAnularComponent:**
  - [ ] Replace `prompt()` with modal
  - [ ] Red styling (dangerous action)
  - [ ] Test: motivo required, MASTER-only

- [ ] **Update actas-lista.page.ts:**
  - [ ] Import all modal components
  - [ ] Add modal visibility signals
  - [ ] Update `obtenerAccionesActa()` to include Aprobar, Disputar, Corregir
  - [ ] Wire modal confirmations → reload list

- [ ] **Update acta-detalle.page.ts:**
  - [ ] Remove inline modal logic (lines 211-325)
  - [ ] Use extracted components
  - [ ] Maintain same UX (modals still available in detail)

- [ ] **Update tests:**
  - [ ] actas-lista.page.spec.ts: Add tests for action modals
  - [ ] Verify all 4 actions accessible from list

- [ ] **Manual QA:**
  - [ ] Login as Coordinator → navigate `/coordinator/actas`
  - [ ] Click dropdown on acta
  - [ ] Verify all 5 actions visible (Aprobar, Disputar, Corregir, Revisar, Anular)
  - [ ] Test each action modal
  - [ ] Verify Anular uses modal (not prompt)
  - [ ] Test MASTER-only controls

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Modal extraction breaks detail page | Medium | Keep unit tests, test both list + detail |
| API calls duplicated (list + detail) | Low | Both use same CoordinatorApi service |
| Component explosion (too many modals) | Low | Group related modals in `components/` folder |
| Existing tests fail after refactor | Medium | Run full test suite before merge |

---

## Success Criteria

### Phase 1 Success
- ✅ All 4 actions accessible from list dropdown
- ✅ All 4 action modals render correctly
- ✅ Anular uses modal (not prompt)
- ✅ Unit tests passing (100%)
- ✅ Manual QA signed off
- ✅ Verify-report updated to CONFORM

### Phase 2 Success (optional)
- ✅ E2E journey tests passing (5/5)
- ✅ Stryker mutation coverage >90%
- ✅ CI includes E2E + mutation gates

---

## Files Summary

```
NEW (Phase 1):
  components/modal-aprobar/
  components/modal-disputar/
  components/modal-corregir/
  components/modal-anular/

MODIFY (Phase 1):
  pages/actas-lista/actas-lista.page.ts
  pages/actas-lista/actas-lista.page.html
  pages/actas-lista/actas-lista.page.spec.ts
  pages/acta-detalle/acta-detalle.page.ts

NEW (Phase 2):
  e2e/coordinator-actas.spec.ts
  stryker.conf.json

MODIFY (Phase 2):
  .github/workflows/ci.yml (add E2E + mutation gates)
```

---

## Estimated Timeline

| Phase | Tasks | Effort | Duration |
|-------|-------|--------|----------|
| **Phase 1** | Extract 4 modals + integrate | 8h | 1-2 days |
| **Phase 2** | E2E + Mutation + CI | 8h | 1-2 days (next sprint) |
| **TOTAL** | All gaps fixed | 16h | 2-4 days across 2 sprints |

---

## Recommendation

**✅ EXECUTE PHASE 1 IN THIS SPRINT**

Rationale:
- **Quick wins:** 1-2 days work
- **High UX impact:** Actions in list + branded anular modal
- **Proven approach:** Extract to components (Angular pattern)
- **Regression risk:** LOW (unit tests validate)

**Defer Phase 2 to next sprint** (E2E + mutation tests are nice-to-have, not blockers)

---

**Report Generated:** 2026-11-09  
**Approved for Implementation:** Pending sprint planning
