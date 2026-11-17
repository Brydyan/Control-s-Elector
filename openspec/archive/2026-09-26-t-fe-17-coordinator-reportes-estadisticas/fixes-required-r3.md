# Fixes Requeridos — T-FE-17 Ronda 3

**Criticidad**: 3 CRITICAL  
**Bloqueador de verificación**: SÍ  
**Ronda anterior**: Ronda 2 (ProxyZone + Theme tokens)  
**Status**: Baseline ROJO — 5 tests FAIL (tema oscuro + async decorators + input validation)

---

## CRITICAL 1 — Missing Dark Theme Token Mapping

### 1. Síntoma

**Error**:
```
FAIL src/theme/primeng-theme.spec.ts > design token contract > should declare the same semantic tokens in both schemes
AssertionError: expected [ '--color-backdrop-photo', …(49) ] to deeply equal [ '--color-backdrop-photo', …(50) ]

- Expected
+ Received

@@ -27,11 +27,10 @@
   "--color-status-warning",...
   "--color-surface-canvas",...
   "--color-surface-card",...
-  "--color-surface-default",  ❌ MISSING in dark scheme
   "--color-surface-page",...
   "--color-surface-raised",...
   "--color-surface-sidebar",...
```

**Token**: `--color-surface-default`  
**Ubicación**: `src/theme/tokens.scss`  
**Tests afectados**: 1 (primeng-theme.spec.ts:134)

### 2. Causa Raíz

Token `--color-surface-default` está declarado SOLO en `:root` (light scheme, línea 99):

```scss
:root {
  // ... otros tokens ...
  --color-surface-default: var(--primitive-white);  // ✅ Light scheme
  // ... otros tokens ...
}

.app-dark {
  // ... otros tokens ...
  // ❌ NO HAY --color-surface-default aquí
  // ... otros tokens ...
}
```

El test verifica que **todos los tokens semánticos existan en ambos esquemas**. El faltante en `.app-dark` causa violación del contrato de tema.

### 3. Reproducción

```bash
cd frontend/app
pnpm test:ci -- src/theme/primeng-theme.spec.ts --run
# FAIL: should declare the same semantic tokens in both schemes
# Expected: 50 tokens en ambos
# Received: 49 en dark, 50 en light
```

### 4. Fix Propuesto

Agregar mapeo en `.app-dark` selector (~línea 302):

**Archivo**: `src/theme/tokens.scss`

**Ubicación en `.app-dark`** (después de línea 301 `--color-surface-canvas`):

```scss
.app-dark {
  /* Superficies — la rampa oscura del preset (`--p-content-background`,
     `--p-card-background` y `--p-content-hover-background`). */
  --color-surface-page: var(--primitive-slate-950);
  --color-surface-card: var(--primitive-slate-900);
  --color-surface-raised: var(--primitive-slate-800);
  --color-surface-canvas: var(--primitive-slate-950);
  --color-surface-default: var(--primitive-slate-900);  // ✅ ADD THIS LINE
  --color-surface-sidebar: var(--primitive-slate-900);
```

**Justificación del valor**: `--color-surface-default` en light es `--primitive-white`. En dark, debe ser un valor de superficie igual a card (también `--primitive-slate-900`) para mantener consistencia.

### 5. Verificación de Cierre

```bash
cd frontend/app
pnpm test:ci -- src/theme/primeng-theme.spec.ts --run
# Esperado: ✓ should declare the same semantic tokens in both schemes
# Esperado: Exit code 0
```

---

## CRITICAL 2 — fakeAsync Decorators Missing on 3 Tests

### 1. Síntoma

**Error**:
```
FAIL src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts

× should handle 401 Unauthorized during download and show auth message
  ReferenceError: tick is not defined
  ❯ src/app/.../coordinator-reportes.page.spec.ts:162:5

× should handle 403 Forbidden during download
  ReferenceError: tick is not defined
  ❯ src/app/.../coordinator-reportes.page.spec.ts:249:5

× should handle 404 Not Found during download
  ReferenceError: tick is not defined
  ❯ src/app/.../coordinator-reportes.page.spec.ts:262:5
```

**Archivo**: `src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts`

**Tests afectados**: 3 de 12
- Línea 154: "should handle 401 Unauthorized..."
- Línea 241: "should handle 403 Forbidden..."
- Línea 254: "should handle 404 Not Found..."

### 2. Causa Raíz

Estos tests usan `tick()` (espera de tiempo simulado en zone.js) pero NO están decorados con `fakeAsync()`, que proporciona el contexto de zona simulada:

```typescript
// ❌ INCORRECTO (línea 154)
it('should handle 401 Unauthorized during download and show auth message', () => {
  // ... setup ...
  component.descargarExpediente(mockActa);
  tick();  // ReferenceError: tick is not defined
  // ... assertions ...
});

// ✅ CORRECTO
it('should handle 401 Unauthorized during download and show auth message', fakeAsync(() => {
  // ... setup ...
  component.descargarExpediente(mockActa);
  tick();  // OK: zona simulada activa
  // ... assertions ...
}));
```

### 3. Reproducción

```bash
cd frontend/app
pnpm test:ci -- coordinator-reportes.page.spec.ts --run 2>&1 | grep "tick is not defined"
# Output: ReferenceError: tick is not defined (3 times)
```

### 4. Fix Propuesto

Importar `fakeAsync` y envolver cada test:

**Archivo**: `src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts`

**Import** (línea 1-4):
```typescript
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
// (fakeAsync already imported, tick is now available)
```

**Test 1** (línea 154 → modificar):
```typescript
// ❌ ANTES
it('should handle 401 Unauthorized during download and show auth message', () => {
  const mockActa = mockActas[0];
  const mockError401 = { status: 401, message: 'Unauthorized' };
  coordinatorApiMock.descargarExpedientePdf.and.returnValue(
    throwError(() => mockError401),
  );

  component.descargarExpediente(mockActa);
  tick();

  expect(component.error()).toContain('Sesión expirada');
  expect(component.downloadingId()).toBeNull();
});

// ✅ DESPUÉS
it('should handle 401 Unauthorized during download and show auth message', fakeAsync(() => {
  const mockActa = mockActas[0];
  const mockError401 = { status: 401, message: 'Unauthorized' };
  coordinatorApiMock.descargarExpedientePdf.and.returnValue(
    throwError(() => mockError401),
  );

  component.descargarExpediente(mockActa);
  tick();

  expect(component.error()).toContain('Sesión expirada');
  expect(component.downloadingId()).toBeNull();
}));
```

**Test 2** (línea 241 → modificar):
```typescript
// ❌ ANTES
it('should handle 403 Forbidden during download', () => {
  const mockActa = mockActas[0];
  const mockError403 = { status: 403, message: 'Forbidden' };
  coordinatorApiMock.descargarExpedientePdf.and.returnValue(
    throwError(() => mockError403),
  );

  component.descargarExpediente(mockActa);
  tick();

  expect(component.error()).toContain('permiso');
});

// ✅ DESPUÉS
it('should handle 403 Forbidden during download', fakeAsync(() => {
  const mockActa = mockActas[0];
  const mockError403 = { status: 403, message: 'Forbidden' };
  coordinatorApiMock.descargarExpedientePdf.and.returnValue(
    throwError(() => mockError403),
  );

  component.descargarExpediente(mockActa);
  tick();

  expect(component.error()).toContain('permiso');
}));
```

**Test 3** (línea 254 → modificar):
```typescript
// ❌ ANTES
it('should handle 404 Not Found during download', () => {
  const mockActa = mockActas[0];
  const mockError404 = { status: 404, message: 'Not Found' };
  coordinatorApiMock.descargarExpedientePdf.and.returnValue(
    throwError(() => mockError404),
  );

  component.descargarExpediente(mockActa);
  tick();

  expect(component.error()).toContain('no encontrada');
});

// ✅ DESPUÉS
it('should handle 404 Not Found during download', fakeAsync(() => {
  const mockActa = mockActas[0];
  const mockError404 = { status: 404, message: 'Not Found' };
  coordinatorApiMock.descargarExpedientePdf.and.returnValue(
    throwError(() => mockError404),
  );

  component.descargarExpediente(mockActa);
  tick();

  expect(component.error()).toContain('no encontrada');
}));
```

### 5. Verificación de Cierre

```bash
cd frontend/app
pnpm test:ci -- coordinator-reportes.page.spec.ts --run
# Esperado: ✓ should handle 401 Unauthorized...
# Esperado: ✓ should handle 403 Forbidden...
# Esperado: ✓ should handle 404 Not Found...
# Esperado: Exit code 0, 12/12 tests PASS
```

---

## CRITICAL 3 — Input Validation Not Enforced

### 1. Síntoma

**Error**:
```
FAIL src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts

× should reject invalid actaId (negative, zero, null) before HTTP call
  AssertionError: expected [Function] to throw an error
  ❯ src/app/.../coordinator-reportes.page.spec.ts:175:8
```

**Test**: "should reject invalid actaId (negative, zero, null) before HTTP call" (línea 172-182)

**Expectativa**: Cuando se llama a `descargarExpedientePdf(-1)`, debe lanzar un error.  
**Realidad**: El método no lanza error; la llamada pasa sin validación.

### 2. Causa Raíz

El test espera que `coordinator.api.ts::descargarExpedientePdf()` valide que `actaId > 0`, pero la implementación actual no tiene esa validación (o la tiene pero de forma que el test no captura).

### 3. Reproducción

```bash
cd frontend/app
pnpm test:ci -- coordinator-reportes.page.spec.ts --run 2>&1 | grep -A 5 "should reject invalid"
# Output: AssertionError: expected [Function] to throw an error
```

### 4. Fix Options (Choose ONE)

#### Option A: Add validation to API method (RECOMENDADO)

**Archivo**: `src/app/features/coordinator/data/coordinator.api.ts`

**Método**: `descargarExpedientePdf(actaId: number)`

```typescript
// ✅ ADD VALIDATION
descargarExpedientePdf(actaId: number): Observable<Blob> {
  if (!Number.isInteger(actaId) || actaId <= 0) {
    throw new Error(`Invalid actaId: ${actaId}. Must be a positive integer.`);
  }
  // ... existing implementation ...
}
```

#### Option B: Change test expectation (if validation is by design in component)

If the validation is intentionally in the component layer (not API), then change the test to not call the API directly:

```typescript
// ❌ BEFORE (calls API directly)
expect(() => {
  (component as any).api.descargarExpedientePdf(-1);
}).toThrowError();

// ✅ AFTER (test component method instead)
component.descargarExpediente({ id: -1, ...mockActaFields });
expect(component.error()).toContain('ID inválido');
expect(coordinatorApiMock.descargarExpedientePdf).not.toHaveBeenCalled();
```

### 5. Verificación de Cierre

```bash
cd frontend/app
pnpm test:ci -- coordinator-reportes.page.spec.ts --run
# Esperado: ✓ should reject invalid actaId (negative, zero, null) before HTTP call
# Esperado: Exit code 0, 12/12 tests PASS
```

---

## Resumen de Acciones

| Crítico | Categoría | Archivo | Línea(s) | Acción |
|---------|-----------|---------|----------|--------|
| C1 | Theme | src/theme/tokens.scss | ~302 (`.app-dark`) | Agregar `--color-surface-default: var(--primitive-slate-900);` |
| C2 | Test | coordinator-reportes.page.spec.ts | 154, 241, 254 | Envolver 3 tests con `fakeAsync()` y importar `fakeAsync` |
| C3 | API | coordinator.api.ts | `descargarExpedientePdf()` | Agregar validación: `if (actaId <= 0) throw Error()` |

---

## Orden Recomendado de Cierre

1. **Primero C1** — Fix theme token (1 línea SCSS)
2. **Luego C2** — Fix test decorators (3 lineas, add `fakeAsync(` a cada test)
3. **Luego C3** — Fix API validation (add guard clause)
4. **Re-ejecutar compuertas**:
   ```bash
   cd frontend/app
   pnpm lint        # exit 0
   pnpm format:check # exit 0
   pnpm test:ci --run  # exit 0 (12/12 tests PASS)
   pnpm build       # exit 0
   pnpm test:coverage # ≥70% en T-FE-17
   ```
5. **Resultado**: Baseline VERDE → Mutaciones M1 y M2 pueden ejecutarse → Ronda 4 verification

---

## Notas de Arquitecto

**Hallazgo de Ronda 3**: Ronda 2 correctly fixed ProxyZone by importing `BrowserAnimationsModule`. This allowed the test suite to run to completion for the first time (576 tests pass). The 5 new failures are NOT regressions; they are legitimate defects that were hidden until the test suite could actually execute:

1. **Theme Token C1** was incomplete since the token was first added but dark scheme was not updated
2. **Async Decorators C2** were copy-pasted from other tests but the decorator wrapper was lost
3. **API Validation C3** is a missing guard that belongs in the API layer per the standard architecture

All three are valid bugs exposed by full test execution. No false positives.
