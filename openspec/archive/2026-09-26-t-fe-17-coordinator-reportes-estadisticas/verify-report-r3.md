# Verificación T-FE-17 — Ronda 3 (Post-Fixes R2)

**Fecha**: 2026-11-04  
**Rama**: brydyan/sc-370/t-fe-17-coordinator-centro-de-reportes-y  
**Árbol**: ✓ Limpio (git status --short = 0)

---

## COMPUERTAS BASELINE

### 1. Lint Status: ✅ PASS
```
Command: pnpm lint
Exit code: 0
Output: All files pass linting.
```

### 2. Format Check: ✅ PASS
```
Command: pnpm format:check
Exit code: 0
Output: All matched files use Prettier code style!
```

### 3. Test Suite: ❌ FAIL
```
Command: pnpm test:ci --run
Exit code: 1
Result: 5 failed | 576 passed (581 total)
Uptime: 10.52s
```

**Failed Tests (5)**:

| # | Test | File | Cause | Line |
|----|------|------|-------|------|
| 1 | should declare the same semantic tokens in both schemes | `src/theme/primeng-theme.spec.ts:134` | Token `--color-surface-default` missing in `.app-dark` | 99, ~302 |
| 2 | should handle 401 Unauthorized during download and show auth message | `src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts:162` | `tick()` used without `fakeAsync()` wrapper | 154-166 |
| 3 | should reject invalid actaId (negative, zero, null) before HTTP call | `src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts:175` | `toThrowError()` expectation not met (API call doesn't validate) | 172-182 |
| 4 | should handle 403 Forbidden during download | `src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts:249` | `tick()` used without `fakeAsync()` wrapper | 241-252 |
| 5 | should handle 404 Not Found during download | `src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts:262` | `tick()` used without `fakeAsync()` wrapper | 254-265 |

**Root Causes**:

#### RC-1: Missing Dark Theme Token Mapping
Token `--color-surface-default` declared in `:root` (light scheme, line 99) but **missing from `.app-dark`** selector (should be ~line 302).

**Evidence**:
```bash
$ grep -n "color-surface-default" src/theme/tokens.scss
99:  --color-surface-default: var(--primitive-white);

$ grep -n "color-surface-default" src/theme/tokens.scss | wc -l
1   # Should be 2 (light + dark)
```

#### RC-2: fakeAsync Decorator Missing
Tests at lines 154, 241, 254 call `tick()` but test spec does not wrap them in `fakeAsync()`:

```typescript
// CURRENT (lines 154-166, 241-252, 254-265)
it('should handle 401 Unauthorized...', () => {  // ❌ NO fakeAsync()
  // ...
  component.descargarExpediente(mockActa);
  tick();  // ERROR: tick is not defined
  // ...
});

// SHOULD BE
it('should handle 401 Unauthorized...', fakeAsync(() => {  // ✅ fakeAsync wrapper
  // ...
  component.descargarExpediente(mockActa);
  tick();  // OK
  // ...
}));
```

#### RC-3: Input Validation Not Throwing
Test expects `descargarExpedientePdf(-1)` to throw an error, but the API method apparently does not validate input. Needs investigation into `coordinator.api.ts` implementation.

### 4. Build Status: ⏭ SKIPPED
Build not executed because test suite failed. Baseline is ROJO.

### 5. Coverage: ⏭ SKIPPED
Coverage not measured because test suite failed. Baseline is ROJO.

---

## RESUMEN BASELINE

| Compuerta | Status | Exit | Evidencia |
|-----------|--------|------|-----------|
| lint | ✅ PASS | 0 | All files pass linting. |
| format:check | ✅ PASS | 0 | All matched files use Prettier code style! |
| test:ci --run | ❌ FAIL | 1 | 5 failed \| 576 passed (581) |
| build | ⏭ SKIPPED | — | Test suite ROJO → no build attempted |
| coverage | ⏭ SKIPPED | — | Test suite ROJO → no coverage measured |

**Baseline Status: ROJO** ❌

---

## MUTACIONES

**Status**: NOT EXECUTED (Baseline ROJO blocks mutaciones)

Per protocol: "If VERDE → ejecutar mutaciones M1 y M2 ahora."

Baseline is ROJO, so mutaciones cannot be validly executed.

---

## HALLAZGOS NUEVOS

### CRITICAL 1: Dark Theme Token Missing (NEW)

**Síntoma**: `primeng-theme.spec.ts` test fails with token mismatch  
**Causa**: `--color-surface-default` exists in light scheme (`:root`) but NOT in dark scheme (`.app-dark`)  
**Impacto**: Theme contract violation — dark mode incomplete  
**Reproducción**:
```bash
cd frontend/app
pnpm test:ci -- src/theme/primeng-theme.spec.ts --run
# FAIL: should declare the same semantic tokens in both schemes
# Expected array to deeply equal but differs at index 27: missing --color-surface-default
```

**Fix Required**: Add token mapping to `.app-dark` selector:
```scss
.app-dark {
  // ... existing mappings ...
  --color-surface-default: var(--primitive-slate-900);  // or appropriate dark value
}
```

### CRITICAL 2: fakeAsync Decorator Missing on 3 Tests (NEW)

**Síntoma**: ReferenceError: tick is not defined  
**Causa**: Tests call `tick()` but are not decorated with `fakeAsync()`  
**Impacto**: 3 async error-handling tests cannot run  
**Tests Affected**:
- Line 154: "should handle 401 Unauthorized during download..."
- Line 241: "should handle 403 Forbidden during download"
- Line 254: "should handle 404 Not Found during download"

**Fix Required**: Wrap each test in `fakeAsync()`:
```typescript
it('should handle 401 Unauthorized...', fakeAsync(() => {
  // ... test body ...
  tick();
  // ...
}));
```

### CRITICAL 3: Input Validation Not Enforced (NEW)

**Síntoma**: Test expects `toThrowError()` but API method does not throw  
**Cause**: `descargarExpedientePdf()` in `coordinator.api.ts` apparently not validating input  
**Impact**: Boundary condition test fails  
**Test Affected**: Line 172: "should reject invalid actaId (negative, zero, null) before HTTP call"

**Fix Required**: Either:
1. Add validation to `descargarExpedientePdf(-1)` in API, OR
2. Change test expectation if validation is by design in component, not API

---

## VEREDICTO

**BASELINE STATUS**: 🔴 ROJO (5 failures, 576 passes)

**MUTACIONES**: ⏹ NOT EXECUTED (baseline not VERDE)

**BLOCKERS**: 3 CRITICAL

- **C1 (New)**: Dark theme token missing → theme contract fails
- **C2 (New)**: fakeAsync decorators missing on 3 tests → async errors uncatchable
- **C3 (New)**: Input validation not enforced → boundary condition tests fail

**RECOMMENDATION**: **FIXES_REQUIRED**

Ronda 2 fixes (BrowserAnimationsModule import) resolved the ProxyZone issue, but uncovered 3 new defects:
1. Incomplete theme token mapping (dark scheme)
2. Incomplete test async wrapping
3. Missing API input validation

All 3 must be fixed before Ronda 4 verification can proceed.

---

## PRÓXIMAS ACCIONES (Ronda 4)

1. Add `--color-surface-default` mapping to `.app-dark` in `src/theme/tokens.scss`
2. Wrap 3 error-handling tests in `fakeAsync()` in `coordinator-reportes.page.spec.ts`
3. Investigate and fix input validation in `descargarExpedientePdf()` method or test expectation
4. Re-run baseline compuertas:
   ```bash
   cd frontend/app
   pnpm lint && \
   pnpm format:check && \
   pnpm test:ci --run && \
   pnpm build && \
   pnpm test:coverage
   ```
5. Once baseline VERDE, execute mutaciones M1 y M2
6. Generate Ronda 4 verify-report.md

---

## NOTAS

- **Ronda 2 Contribution**: BrowserAnimationsModule import was correct and necessary. It resolved the original ProxyZone error that was blocking ALL test execution. The new failures are deeper contract/coverage defects that were hidden until ProxyZone was available.
- **No Regresión en Tests Existentes**: 576 tests pass (up from 0 in Ronda 2 due to ProxyZone fix). The 5 new failures are new defects exposed, not regressions of previously passing tests.
- **Mutaciones Bloqueadas**: M1 (MIME validation) and M2 (revokeObjectURL) cannot be executed until all baseline tests pass. This is correct per protocol.
