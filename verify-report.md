# Verificación T-FE-17: Centro de Reportes y Estadísticas de Coordinación

**Rama**: brydyan/sc-370/t-fe-17-coordinator-centro-de-reportes-y  
**HEAD**: 7c92e0b1e612c9e9d72cdd5fb223a42db8667783  
**Fecha**: 2026-11-04  
**Ronda**: 1 (Verificación inicial)

---

## Preguntas Respondidas

### P3: ¿Rutas protegidas con roleGuard?

**Resultado**: ✅ **PASS**

Verificación en `app.routes.ts`, líneas 169-182:
```typescript
{
  path: 'coordinator/reportes',
  canActivate: [roleGuard],  // ✅ Presente
  loadComponent: () => ...,
  data: {
    roles: ['COORDINATOR', 'MASTER', 'ADMIN'],  // ✅ Roles correctos
    ...
  }
}
```

La ruta `/coordinator/reportes` está protegida con `roleGuard` y los roles coinciden con la especificación (COORDINATOR, MASTER, ADMIN).

---

### P4: ¿Tests verde sin cambios previos?

**Resultado**: ❌ **BLOQUEADOR — Tests en ROJO**

**Comando ejecutado**:
```bash
cd frontend/app && rtk pnpm test:ci --run
```

**Resultados**:
- **Test Files**: 4 fallaron | 61 pasaron = **65 total** (92.3% pass rate)
- **Tests**: 24 fallaron | 557 pasaron = **581 total** (95.9% pass rate)
- **Exit code**: 1 (ERROR)

**Análisis de fallos**:

#### Suite: `coordinator.api.spec.ts` (12 tests, TODOS fallando)
- Causa: `Logger` no es exportado de `@angular/core`
- Síntoma en CoordinatorApi.ts línea 2: `import { ..., Logger } from '@angular/core'`
- Angular core NO tiene un export named `Logger`
- El servicio usa `this.logger` pero nunca se inyecta correctamente

#### Suite: `token-guard.spec.ts` (1 fallo de 3)
- **Violación 1**: `--color-surface-default` no declarado en `coordinator-reportes.page.scss:3`
- **Violación 2**: `--color-status-danger-strong` no declarado en `coordinator-reportes.page.scss:44`
- **Violación 3-5**: 3 raw colours (rgba) en líneas 59, 100, 118 sin usar theme tokens

#### Suite: `primeng-theme.spec.ts` (1 fallo de 3)
- Mismo problema: 2 tokens no declarados

#### Suite: `coordinator-reportes.page.spec.ts` (10 fallos de 12)
- Causa: El test suite falla porque **CoordinatorApi falla a inyectarse** (Logger undefined)
- 1 test pasa: `should initialize with loading state true` (no toca API)
- 10 tests fallan: todos los que llaman a `cargarMetricas()` o `descargarExpediente()`

---

### P5: ¿Lint y build exitosos?

**Lint**: ✅ **PASS**
```
$ ng lint
All files pass linting.
Exit code: 0
```

**Format:check**: ✅ **PASS**
```
$ prettier --check "src/**/*.{ts,html,scss}"
All matched files use Prettier code style!
Exit code: 0
```

**Build**: ❌ **BLOQUEADOR — ERROR DE COMPILACIÓN**

```
$ ng build
ERROR TS2305: Module '"@angular/core"' has no exported member 'Logger'.
  src/app/features/coordinator/data/coordinator.api.ts:2:29:
    2 │ import { Injectable, inject, Logger } from '@angular/core';

ERROR TS2571: Object is of type 'unknown'.
  src/app/features/coordinator/data/coordinator.api.ts:360:6
  src/app/features/coordinator/data/coordinator.api.ts:367:4
```

**Exit code**: 1 (ERROR)

---

### P6: ¿Accesibilidad y estilos tema 100%?

#### Accesibilidad en `coordinator-reportes.page.html`:

**Hallazgos positivos** ✅:
- Línea 8: `role="alert"` en error banner
- Línea 20: `role="region" aria-label="Progreso operativo global"`
- Línea 66: `role="region" aria-label="Progreso por cantón"`
- Línea 83: `role="region" aria-label="Distribución de actas por estado"`
- Línea 103: `role="region" aria-label="Progreso por cargo electoral"`
- Línea 138: `role="grid" aria-label="Actas registradas"`
- Línea 165: `[attr.aria-label]="'Descargar expediente de acta ' + acta.id"` en botones

**Conclusión**: Accesibilidad bien documentada con ARIA labels.

#### Theme tokens en `coordinator-reportes.page.scss`:

**Hallazgos negativos** ❌:
- Línea 3: `background-color: var(--color-surface-default)` **NO DECLARADO**
- Línea 19: `background-color: var(--color-status-danger-bg)` ✅ OK
- Línea 22: `border: 1px solid var(--color-status-danger-border)` ✅ OK
- Línea 44: `background-color: var(--color-status-danger-strong)` **NO DECLARADO**
- Línea 59: `box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1)` **RAW COLOUR (rgba)**
- Línea 100: `background-color: rgba(255, 255, 255, 0.2)` **RAW COLOUR (rgba)**
- Línea 118: `background-color: rgba(255, 255, 255, 0.1)` **RAW COLOUR (rgba)**
- Resto: `--color-brand-primary`, `--color-text-primary`, `--color-border-subtle`, etc. ✅ OK

**Conclusión**: 7 violaciones de tema encontradas (5 raw colours + 2 tokens no declarados). Falla validación.

---

## Mutaciones Ejecutadas

**Estado**: No ejecutadas (bloqueadas por errores previos)

**Razón**: El baseline está en rojo. La política de verificación exige que las mutaciones solo se ejecuten cuando el baseline está en verde. Los bloqueadores críticos deben cerrarse primero.

---

## Compuertas Ejecutadas

| Compuerta | Resultado | Notas |
|-----------|-----------|-------|
| `rtk git status --short` | ✅ Limpio (excepto .archify) | Ignorados archivos decorativos |
| `pnpm lint` | ✅ PASS | Exit 0, todo OK |
| `pnpm format:check` | ✅ PASS | Exit 0, Prettier OK |
| `pnpm test:ci --run` | ❌ FAIL | Exit 1, 24 tests fallando |
| `pnpm build` | ❌ FAIL | Exit 1, TS2305 error de compilación |

---

## Accesibilidad & Estilos

### Accesibilidad (HTML)
- **Estado**: ✅ Bien implementado
- **Hallazgo**: Todos los componentes tienen `role` y `aria-label` apropiados
- **Cobertura**: 100% de regions y botones

### Theme Tokens (SCSS)
- **Estado**: ❌ 7 violaciones
- **Hallazgo**: 
  - 2 tokens no declarados: `--color-surface-default`, `--color-status-danger-strong`
  - 5 raw colours (rgba) sin usar theme layer
- **Impacto**: Falla validación de token-guard.spec.ts

---

## Hallazgos / Deuda Técnica

### CRÍTICOS (Bloquean ejecución)

1. **Logger no importable de @angular/core**
   - Ubicación: `coordinator.api.ts:2`
   - Impacto: Falla compilación + 12 tests de API no corren
   - Tipo: Error de importación

2. **Theme tokens no declarados**
   - Ubicación: `coordinator-reportes.page.scss:3, :44`
   - Tokens: `--color-surface-default`, `--color-status-danger-strong`
   - Impacto: Falla token-guard.spec.ts

3. **Raw colours sin theme tokens**
   - Ubicación: `coordinator-reportes.page.scss:59, :100, :118`
   - Valores: `rgba(0, 0, 0, 0.1)`, `rgba(255, 255, 255, 0.2)`, `rgba(255, 255, 255, 0.1)`
   - Impacto: Falla validación de tokens

### ADVERTENCIAS

1. **Unused imports en CoordinatorReportesPage**
   - `ErrorStateComponent` importado pero no usado (línea 54)
   - Refactorizar imports

---

## Veredicto

**REWORK REQUERIDO**

El código T-FE-17 **no puede ser verificado** en su estado actual:

1. ✅ Rutas correctamente protegidas (P3)
2. ✅ HTML accesible (P6.1)
3. ❌ Compilación fallida — Logger no existe (BLOQUEADOR)
4. ❌ Tests en rojo — 24/581 fallan (BLOQUEADOR)
5. ❌ Estilos/theme — 7 violaciones de tokens (BLOQUEADOR)

**Mutaciones M1 y M2 diferidas** hasta que se cierren los bloqueadores.

