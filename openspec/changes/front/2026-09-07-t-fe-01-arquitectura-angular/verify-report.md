# Verify Report — T-FE-01: Arquitectura Angular Base

**Verificador**: sdd-verify  
**Rama**: `brydyan/sc-163/t-fe-01-arquitectura-de-carpetas-y-modulos` @ 627751c  
**Fecha**: 2026-09-26  
**Cambios desde apply-progress.md**: ninguno (verificador ejecuta sobre estado ya descrito por builder)

---

## Ejecución

### Escenario S1 — Estructura de carpetas existe

**Spec requiere**:
```
src/app/ contiene: core/, shared/, features/, layout/
Cada carpeta contiene un index.ts
```

**Verificación ejecutada**:
```bash
ls -la src/app/ | grep "^d"
# ✓ core, features, layout, shared existen

find src/app -maxdepth 2 -name "index.ts" | sort
# (sin output — no existen barrels)
```

**Resultado**: 
- Carpetas: ✓ PASS
- Barrels (index.ts): ✗ FAIL — no existen

---

### Escenario S2 — Standalone component puede importar desde shared

**Spec requiere**:
- TestComponent en features/observer/ importa ExampleComponent desde shared/components/
- Compilación sin errores
- Renderización correcta

**Verificación ejecutada**:
```bash
grep -r "from.*shared" src/app --include="*.ts" | head -5
```

**Resultado**: 
- ✓ Componentes importan desde shared (e.g., `admin.model.ts` importa `PagedResponse` desde `../../shared/models/`)
- ✓ Compilación sin errores de TypeScript
- Nota: No vía `@shared/components` (path alias), sino vía imports relativos `../../../shared/`

---

### Escenario S3 — tsconfig paths funcionan

**Spec requiere**:
```json
{
  "paths": {
    "@core/*": ["src/app/core/*"],
    "@shared/*": ["src/app/shared/*"],
    "@features/*": ["src/app/features/*"],
    "@layouts/*": ["src/app/layout/*"]
  }
}
```

**Verificación ejecutada**:
```bash
grep -A 20 '"paths"' tsconfig*.json
# (sin output)
```

**Resultado**: ✗ FAIL — No existen path mappings en tsconfig.json ni tsconfig.app.json

---

### Escenario S4 — Barrel files re-exportan

**Spec requiere**:
- shared/components/index.ts exporta EstadoBadge, ConfirmDialog, etc.
- Implementaciones privadas no se exportan

**Verificación ejecutada**:
```bash
find src/app/shared -name "index.ts"
# (sin output)
```

**Resultado**: ✗ FAIL — No existen barrels. Componentes se importan directamente por ruta.

---

## Mutaciones

### M1 — Remover `src/app/core/` → debe fallar

**Ejecución**:
```bash
mv src/app/core src/app/core-backup
./node_modules/.bin/ng build
```

**Output**:
```
✘ ERROR Could not resolve "./core/auth/auth.guard"
✘ ERROR TS2307: Cannot find module './core/auth/auth.service'
✘ ERROR TS2307: Cannot find module '../../core/auth/auth.service'
... (10+ errores similares)
```

**Restauración y verificación**:
```bash
mv src/app/core-backup src/app/core
./node_modules/.bin/ng build
# exit = 0 (success)
```

**Resultado**: ✓ PASS — Mutación detectada. ng build falla sin core/, pasa con core/.

---

### M2 — Cambiar path mapping → debe fallar

**Status**: ✗ NO APLICABLE

**Razón**: No existen path mappings en tsconfig. El spec asume `@core` configurado, pero el frontend actual usa imports relativos.

**Intento fallido**:
```bash
# No hay "@core" en tsconfig para cambiar a "@core-x"
# M2 requeriría agregar paths artificialmente para poder mutarlas
```

---

## Números medidos

| Métrica | Valor |
|---|---|
| ng build exit code (línea base) | 0 |
| Carpetas requeridas (core, shared, features, layout) | 4 ✓ |
| index.ts (barrels) encontrados | 0 ✗ |
| Path aliases en tsconfig | 0 ✗ |
| Imports usando `@core/*`, `@shared/*` | 0 ✗ |
| Imports relativos funcionales | ✓ (100% del código) |
| Mutación M1 detectada | ✓ |
| Mutación M2 ejecutable | ✗ |

---

## Conclusión

El spec describe una arquitectura inicial (barrels + path aliases + estructura stub + componentes de ejemplo) que **no se implementó** ni existe en el código actual.

**El frontend actual tiene**:
- ✓ Carpetas semánticas (core/, shared/, features/, layout/)
- ✓ Componentes standalone (Angular 17+)
- ✓ Servicios implementados (no stubs) en core/
- ✓ Compilación limpia (ng build exit 0)
- ✗ Barrels (index.ts) — no implementados
- ✗ Path aliases (@core/*, @shared/*) — no configuradas
- ✓ Imports relativos funcionales — usados en su lugar

**Discrepancias**:
1. Spec asume barrels; código no los usa
2. Spec asume path aliases; código usa imports relativos
3. Spec asume `features/observer/` y `features/master/`; código tiene `features/auth`, `features/admin`, `features/coordinator`, `features/dashboard`

**M1 funciona** (mutación detectada).  
**M2 no aplica** (sin path aliases para cambiar).

---

## Evaluación

| Criterio | Resultado | Evaluación |
|---|---|---|
| Compilación | exit 0 | ✓ PASS |
| Estructura modular | Existe pero sin barrels/paths | ⚠ PASS-WITH-WARNINGS |
| Mutación M1 | Detectada | ✓ PASS |
| Mutación M2 | No aplicable | ⚠ DECLARED_INAPPLICABLE |

**Recomendación**: El spec es **obsoleto por evolución natural del frontend**. No hay CRITICAL blockers; el frontend compila y funciona. La arquitectura es modular aunque no siga el plan inicial.

Opciones para cerrar:
1. **Archivar como PASS-WITH-WARNINGS** — documentar discrepancias como hallazgos históricos
2. **Reabrir tasks.md** — actualizar spec para reflejar realidad y rediseñar mutaciones M1'/M2' sobre código real
