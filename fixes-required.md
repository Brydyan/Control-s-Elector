# Fixes Requeridos — T-FE-17

**Criticidad**: 3 CRITICAL + 1 WARNING  
**Bloqueador de verificación**: SÍ  
**Podrá continuar mutación**: NO hasta cierre de todos los CRITICAL

---

## CRITICAL 1 — Logger no es exportable desde @angular/core

### 1. Síntoma
- **Error de compilación**: `TS2305: Module '"@angular/core"' has no exported member 'Logger'.`
- **Archivo**: `frontend/app/src/app/features/coordinator/data/coordinator.api.ts`
- **Línea**: 2
- **Comando que falla**: `pnpm build`
- **Resultado**: BUILD FALLIDO, exit code 1

### 2. Causa Raíz

El archivo intenta importar un `Logger` de `@angular/core` que no existe:

```typescript
// LÍNEA 2 — INCORRECT
import { Injectable, inject, Logger } from '@angular/core';
```

Angular 17+ no exporta una clase `Logger` de su módulo core. El servicio usa `this.logger` en líneas 360, 367, etc., pero la inyección falla en runtime.

### 3. Reproducción

```bash
cd frontend/app
pnpm build
# Salida: ERROR TS2305: Module '"@angular/core"' has no exported member 'Logger'.
```

### 4. Fix Propuesto

**Opción A** (Recomendada): Usar un logger simple (console wrapper):

```typescript
// ANTES
import { Injectable, inject, Logger } from '@angular/core';
export class CoordinatorApi {
  private readonly http = inject(HttpClient);
  private readonly logger = inject(Logger);

// DESPUÉS
import { Injectable, inject } from '@angular/core';
export class CoordinatorApi {
  private readonly http = inject(HttpClient);
  private readonly logger = {
    info: console.log,
    error: console.error,
    warn: console.warn,
  };
```

O si existe un servicio Logger personalizado en el proyecto, usarlo:

```typescript
import { Injectable, inject } from '@angular/core';
import { LoggerService } from '../../core/services/logger.service'; // SI EXISTE

export class CoordinatorApi {
  private readonly http = inject(HttpClient);
  private readonly logger = inject(LoggerService);
```

### 5. Verificación de Cierre

Ejecutar:
```bash
cd frontend/app
pnpm build
# Esperado: ✔ Building...
# Exit code: 0
# NO debe haber errores TS2305 ni TS2571
```

---

## CRITICAL 2 — Theme tokens no declarados en token-guard.spec.ts

### 1. Síntoma
- **Test falla**: `should not reference a custom property that is never declared`
- **Archivo**: `frontend/app/src/app/features/coordinator/pages/reportes/coordinator-reportes.page.scss`
- **Tokens no declarados**: 
  - `--color-surface-default` (línea 3)
  - `--color-status-danger-strong` (línea 44)
- **Suite fallida**: `src/theme/token-guard.spec.ts`
- **Comando**: `pnpm test:ci --run`
- **Resultado**: Test falla, exit code 1

### 2. Causa Raíz

El archivo SCSS usa dos custom properties que no están declaradas en el archivo de tokens del tema (`src/theme/tokens.scss` o similar):

```scss
// LÍNEA 3 — REFERENCIAS token no declarado
.coordinator-reportes-container {
  background-color: var(--color-surface-default);  // ❌ NO EXISTE
```

```scss
// LÍNEA 44 — REFERENCIAS token no declarado
.btn-retry {
  background-color: var(--color-status-danger-strong);  // ❌ NO EXISTE
```

### 3. Reproducción

```bash
cd frontend/app
pnpm test:ci -- src/theme/token-guard.spec.ts --run
# Salida:
# FAIL src/theme/token-guard.spec.ts > the token guard > should not find a violation in the app
# AssertionError: expected [ … ] to deeply equal []
# [
#   "undeclared-reference src/app/.../coordinator-reportes.page.scss:3 \"--color-surface-default\" ..."
#   "undeclared-reference src/app/.../coordinator-reportes.page.scss:44 \"--color-status-danger-strong\" ..."
# ]
```

### 4. Fix Propuesto

**Opción A** (Buscar equivalentes existentes):

1. Revisar `src/theme/tokens.scss` para encontrar equivalentes:
   - `--color-surface-default` → Posible `--color-surface`, `--color-bg-default`, o `--color-background`
   - `--color-status-danger-strong` → Posible `--color-danger-700`, `--color-status-danger-emphasis`, o `--color-status-danger-bold`

2. Reemplazar en `coordinator-reportes.page.scss`:
   ```scss
   // LÍNEA 3
   - background-color: var(--color-surface-default);
   + background-color: var(--color-surface);  // O el token correcto
   
   // LÍNEA 44
   - background-color: var(--color-status-danger-strong);
   + background-color: var(--color-status-danger-bold);  // O el token correcto
   ```

**Opción B** (Agregar tokens faltantes):

Si no existen equivalentes, declarar en `src/theme/tokens.scss`:

```scss
--color-surface-default: #f5f5f5;  // O el valor correcto
--color-status-danger-strong: #d32f2f;  // O el valor correcto
```

### 5. Verificación de Cierre

```bash
cd frontend/app
pnpm test:ci -- src/theme/token-guard.spec.ts --run
# Esperado: ✓ should not find a violation in the app
# Exit code: 0
```

---

## CRITICAL 3 — Raw colours (rgba) en SCSS sin theme tokens

### 1. Síntoma
- **Test falla**: `should not find a violation in the app` (token-guard.spec.ts)
- **Archivo**: `frontend/app/src/app/features/coordinator/pages/reportes/coordinator-reportes.page.scss`
- **Raw colours encontrados**:
  - Línea 59: `rgba(0, 0, 0, 0.1)` en `box-shadow`
  - Línea 100: `rgba(255, 255, 255, 0.2)` en `background-color`
  - Línea 118: `rgba(255, 255, 255, 0.1)` en `background-color`
- **Suite fallida**: `src/theme/token-guard.spec.ts`
- **Regla violada**: "raw-colour — is a raw colour outside the theme layer"

### 2. Causa Raíz

El código usa colores RGBA directos en lugar de custom properties del tema:

```scss
// LÍNEA 59 — RGBA directo
.banner-progreso {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);  // ❌ Raw colour

// LÍNEA 100 — RGBA directo
.progress-bar-container {
  background-color: rgba(255, 255, 255, 0.2);  // ❌ Raw colour

// LÍNEA 118 — RGBA directo
.metrica-item {
  background-color: rgba(255, 255, 255, 0.1);  // ❌ Raw colour
```

### 3. Reproducción

```bash
cd frontend/app
pnpm test:ci -- src/theme/token-guard.spec.ts --run
# Salida incluye:
# "raw-colour src/app/.../coordinator-reportes.page.scss:59 \"rgba(0, 0, 0, 0.1)\" is a raw colour"
# "raw-colour src/app/.../coordinator-reportes.page.scss:100 \"rgba(255, 255, 255, 0.2)\" is a raw colour"
# "raw-colour src/app/.../coordinator-reportes.page.scss:118 \"rgba(255, 255, 255, 0.1)\" is a raw colour"
```

### 4. Fix Propuesto

Crear custom properties transparentes en `src/theme/tokens.scss` y referenciarlas:

```scss
// EN src/theme/tokens.scss (crear si no existen)
--color-shadow-light: rgba(0, 0, 0, 0.1);
--color-overlay-light: rgba(255, 255, 255, 0.2);
--color-overlay-subtle: rgba(255, 255, 255, 0.1);
```

Luego en `coordinator-reportes.page.scss`:

```scss
// LÍNEA 59
- box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
+ box-shadow: 0 2px 8px var(--color-shadow-light);

// LÍNEA 100
- background-color: rgba(255, 255, 255, 0.2);
+ background-color: var(--color-overlay-light);

// LÍNEA 118
- background-color: rgba(255, 255, 255, 0.1);
+ background-color: var(--color-overlay-subtle);
```

### 5. Verificación de Cierre

```bash
cd frontend/app
pnpm test:ci -- src/theme/token-guard.spec.ts --run
# Esperado: ✓ should not reference a custom property that is never declared
# Esperado: ✓ should not find a violation in the app
# Exit code: 0
```

---

## WARNING 1 — Unused import: ErrorStateComponent

### 1. Síntoma
- **Angular warning**: `NG8113: ErrorStateComponent is not used within the template`
- **Archivo**: `frontend/app/src/app/features/coordinator/pages/reportes/coordinator-reportes.page.ts`
- **Línea**: 54
- **Severidad**: WARNING (no bloquea compilación)

### 2. Causa Raíz

El componente importa `ErrorStateComponent` pero el template usa un `<div *ngIf="error()">` manual en lugar de delegar al componente:

```typescript
// LÍNEA 54 — Importado pero no usado
imports: [
  CommonModule,
  CardComponent,
  LoadingStateComponent,
  ErrorStateComponent,  // ← NO SE USA
]
```

```html
<!-- Template no lo usa, implementa manualmente -->
<div *ngIf="error()" role="alert" class="error-banner">
  <p>{{ error() }}</p>
  <button (click)="cargarMetricas()" type="button" class="btn-retry">
    Reintentar
  </button>
</div>
```

### 3. Fix Propuesto

**Opción A** (Remover import):
```typescript
imports: [
  CommonModule,
  CardComponent,
  LoadingStateComponent,
  // ErrorStateComponent,  // ← REMOVER
]
```

**Opción B** (Usar el componente):
Si existe un `ErrorStateComponent` reutilizable, reemplazar HTML manual:
```html
<!-- ANTES -->
<div *ngIf="error()" role="alert" class="error-banner">
  <p>{{ error() }}</p>
  <button (click)="cargarMetricas()" type="button" class="btn-retry">
    Reintentar
  </button>
</div>

<!-- DESPUÉS -->
<app-error-state 
  *ngIf="error()" 
  [message]="error()"
  (retry)="cargarMetricas()">
</app-error-state>
```

### 4. Verificación de Cierre

```bash
cd frontend/app
pnpm build
# Esperado: Desaparece warning NG8113: ErrorStateComponent is not used...
# Esperado: Build success, exit code 0
```

---

## Resumen de Acciones

| Crítico | Archivo | Línea(s) | Acción |
|---------|---------|----------|--------|
| C1 | `coordinator.api.ts` | 2 | Quitar import `Logger`, usar logger inline o LoggerService |
| C2 | `coordinator-reportes.page.scss` | 3, 44 | Mapear a tokens existentes o declarar en theme |
| C3 | `coordinator-reportes.page.scss` | 59, 100, 118 | Crear custom properties transparentes en tokens.scss |
| W1 | `coordinator-reportes.page.ts` | 54 | Remover ErrorStateComponent del imports |

---

## Orden Recomendado de Cierre

1. **Primero C1** — Permite que `pnpm build` pase
2. **Luego C2+C3** — Permite que `pnpm test:ci` pase (token-guard.spec.ts y primeng-theme.spec.ts)
3. **Finalmente W1** — Limpia warnings
4. **Resultado**: Baseline verde → Mutaciones M1 y M2 pueden ejecutarse

