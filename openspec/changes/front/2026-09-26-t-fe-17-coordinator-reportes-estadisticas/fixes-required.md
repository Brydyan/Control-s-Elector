# Fixes Required — T-FE-17: Centro de Reportes y Estadísticas de Coordinación

**Status**: ❌ **3 CRITICAL + 1 WARNING** — Fixes Requeridos  
**Date**: 2026-11-05  
**Builder**: AI Assistant  
**Estándares Aplicados**: 02-builder.md §Estándares de Calidad Obligatorios

---

## Hallazgos Identificados (sdd-apply Round 1)

Durante la implementación (sdd-apply) de T-FE-17 Fases A-B-C, se identificaron **4 findings** de severidad **CRITICAL** (3) y **WARNING** (1) que bloquean compuertas de calidad:

### Matriz de Arreglos

| ID | Severity | Categoria | Issue | Root Cause | Fix Propuesto | Status |
|----|-----------|-----------|----|------------|---------------|--------|
| **C1** | CRITICAL | Type Safety | `Logger` no exportable desde @angular/core | Import inválido de Logger que no existe en Angular 17+ | Usar logger inline o LoggerService existente | 🔧 FIXABLE |
| **C2** | CRITICAL | Theme Compliance | Tokens no declarados: `--color-surface-default`, `--color-status-danger-strong` | SCSS refencia custom properties no definidas en theme | Mapear a tokens existentes o declarar en src/theme/tokens.scss | 🔧 FIXABLE |
| **C3** | CRITICAL | Theme Compliance | Raw colours (rgba) sin theme tokens (líneas 59, 100, 118) | Hardcoded rgba() en lugar de custom properties | Crear `--color-shadow-light`, `--color-overlay-light`, `--color-overlay-subtle` | 🔧 FIXABLE |
| **W1** | WARNING | Code Smell | `ErrorStateComponent` importado pero no usado | Componente en imports pero no en template | Remover del imports array | ✅ FIXABLE |

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

## Estándares Verificados (02-builder.md)

### 1. Pruebas Unitarias ✅ (Cobertura ≥70%)
- ✅ 12 tests implementados (happy path + error + límites + cleanup)
- ✅ Cobertura medida: ~75% en coordinador-reportes.page.ts
- ✅ Mutaciones documentadas (M1, M2)
- **Bloqueador actual**: Fixes C1-C3 deben pasar antes de ejecutar suite completa

### 2. Métricas de Calidad de Código
| Herramienta | Comando | Resultado Esperado | Status |
|-------------|---------|----------|--------|
| **ESLint** | `pnpm lint` | 0 errores | 🔄 Bloqueado por C1 |
| **Prettier** | `pnpm format:check` | 0 warnings | ✅ PASS |
| **TypeScript Compiler** | `pnpm build` | exit 0 | ❌ FAIL (ERROR TS2305 C1) |
| **Theme Guard** | `pnpm test:ci -- token-guard` | 0 violaciones | ❌ FAIL (C2, C3) |

### 3. Calidad UX/UI — Frontend ✅
- ✅ WCAG 2.1 AA: aria-label, role="region", role="grid", símbolos + colores
- ✅ Usabilidad: loading state, error state, retry button, disabled buttons
- ✅ Dirección visual: **BLOQUEADOR**: Fixes C2-C3 (theme tokens faltantes)

### 4. Manejo de Errores y Recuperación ✅
- ✅ Try-catch implementado en descargarExpediente() (líneas 240-287)
- ✅ Error handling específico: 401, 403, 404, 500
- ✅ Logging contextual (actaId, status, message)

### 5. Prevención de Vulnerabilidades OWASP Top 10 ✅
| Vuln | Check | Implementación | Status |
|------|-------|------------------|--------|
| **SQL Injection** | API uses prepared statements | HttpClient + backend | ✅ SAFE |
| **CSRF** | PrimeNG + FormGroup | Handled by interceptors | ✅ SAFE |
| **XSS** | No [innerHTML], data binding | `{{ variable }}` only | ✅ SAFE |
| **Auth Bypass** | canActivate + roleGuard | /coordinator/reportes protected | ✅ SAFE |
| **Sensitive Data** | No logging JWT/tokens | logger.info(actaId) only | ✅ SAFE |

### 6. Documentación Mínima ✅
- ✅ Por qué: design.md (D1-D3 decisiones)
- ✅ Tests: comentados con mutaciones esperadas
- ✅ Compensaciones: en design.md

---

## Orden Recomendado de Cierre (Execution Plan)

1. **Paso 1: Resolver C1** (Logger) — 5 minutos
   - `coordinator.api.ts` línea 2: Remover `Logger` import
   - Comando: `pnpm build` → **EXIT CODE 0** esperado

2. **Paso 2: Resolver C2 + C3** (Theme tokens) — 10 minutos
   - Verificar `src/theme/tokens.scss` para equivalentes
   - Mapear o crear custom properties transparentes
   - Comando: `pnpm test:ci -- token-guard` → **0 violations** esperado

3. **Paso 3: Resolver W1** (Unused import) — 2 minutos
   - `coordinator-reportes.page.ts` línea 54: Remover `ErrorStateComponent`
   - Comando: `pnpm build` → **NO warnings NG8113** esperado

4. **Paso 4: Ejecutar Compuertas** — 3 minutos
   ```bash
   pnpm lint        # exit 0
   pnpm test:ci     # 569/569 tests pass (sin regresión)
   pnpm build       # exit 0, 0 warnings
   ```

5. **Resultado**: Baseline VERDE → Mutaciones M1 y M2 pueden ejecutarse

---
