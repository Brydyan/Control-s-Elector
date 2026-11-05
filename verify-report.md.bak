# T-FE-18 Verification Report — Menús Dinámicos RBAC y Navegación Limpia en Sidebar

**Date**: 2026-11-05 03:41 UTC  
**Verificador**: Claude Code  
**Rama**: brydyan/sc-370/t-fe-17-coordinator-centro-de-reportes-y  
**HEAD**: post T-FE-17 archive  

---

## Executive Summary

**Veredicto: FIXES_REQUIRED** (3 critical blockers)

T-FE-18 tiene arquitectura correcta y decisiones técnicas validadas (NAV_CONFIG declarativa, sidebar refactorizado, offline-first), pero **no está listo para producción** por:
1. Duplicados internos en NAV_CONFIG rompen contrato de deduplicación
2. Mock setup de tests no callable como signal
3. Formatting issues no resueltos

---

## Preguntas de Decisión

### P1: ¿NAV_CONFIG declarativa sin duplicados?

**Expectativa**: Record<UserRole, NavSection[]> con 0 duplicados por rol.

**Medición real**:
- **OBSERVER**: 2 items, 2 rutas únicas ✓
- **COORDINATOR**: 4 items, **3 rutas únicas** ✗ (`/coordinator/actas` aparece 2 veces)
- **ADMIN**: 3 items, 3 rutas únicas ✓
- **MASTER**: 11 items, **10 rutas únicas** ✗ (`/coordinator/actas` aparece 2 veces)

**Blocker**: `nav.config.ts` línea 89 (COORDINATOR "Evidencias") y línea 160 (MASTER "Evidencias") asignan `/coordinator/actas` donde debería haber `/coordinator/evidencias` (placeholder que será `/master/configuracion`).

---

### P2: ¿Sidebar refactorizado sin mapRoute() y doble inyección?

**Expectativa**: Código limpio sin procedimientos legados, NAV_CONFIG usado directo.

**Hallazgo**: 
- ✓ `mapRoute()` removido (no existe en sidebar.component.ts)
- ✓ Bloque de doble inyección removido (L138-158 anterior no existe)
- ✓ `ngOnInit()` carga inmediato desde `NAV_CONFIG[role]` → offline-first

**Blocker**: Mock setup. En `sidebar.component.spec.ts` línea 33-38:
```typescript
authStoreMock = jasmine.createSpyObj('AuthStore', [], {
  user: jasmine.createSpy('user').and.returnValue({ ... })
});
```

Luego en sidebar.component.ts línea 68:
```typescript
const role = this.user()?.role
```

**TypeError: this.user is not a function** porque el test asigna `user` como spy directo en la prop, no como signal. El componente espera signal callable.

---

### P3: ¿Cero placeholders en menú?

**Expectativa**: Ninguna ruta en PLACEHOLDER_ROUTES = `['/actas', '/evidencias', '/evidencias/subir', '/admin', '/master/cargos', '/master/partidos', '/master/candidatos']`.

**Hallazgo**: No se puede evaluar porque los duplicados rompen los tests. PLACEHOLDER_ROUTES es un array correcto, pero NAV_CONFIG no lo respeta (tiene `/coordinator/actas` duplicada).

---

### P4: ¿Tests implementados y verdes?

**Resultados**:

#### nav.config.spec.ts
- Total: 9 tests
- Pasando: 7 tests
- Fallido: 2 tests
  - ❌ Role COORDINATOR duplicates (esperaba 3, recibió 4)
  - ❌ Role MASTER duplicates (esperaba 10, recibió 11)

#### sidebar.component.spec.ts
- Total: 16 tests
- Pasando: 1 test
- Fallido: 15 tests (TypeError: this.user is not a function)

**Totales**: 593 PASS / 17 FAIL de 610

---

### P5: ¿Lint, format y build OK?

- Lint: ✓ PASS (exit 0)
- Format: ✗ FAIL (exit 1, sidebar.component.spec.ts)
- Build: ✓ PASS (exit 0)

---

### P6: ¿Accesibilidad ARIA?

✓ PASS — aria-label en nav, aria-hidden en iconos, WCAG 2.1 AA cubierto

---

## Critical Blockers

### Blocker #1: Duplicados en NAV_CONFIG
- Línea 89: COORDINATOR "Evidencias" → `/coordinator/actas` (duplicada)
- Línea 160: MASTER "Evidencias" → `/coordinator/actas` (duplicada)
- Impact: Tests 90 y 152 de nav.config.spec.ts caen

### Blocker #2: Mock setup TypeError
- sidebar.component.spec.ts línea 33-38: `user` no es signal callable
- Impact: 15/16 tests de sidebar fallan

### Blocker #3: Format check fallido
- sidebar.component.spec.ts tiene Prettier issues
- Impact: pnpm format:check exit 1

---

## Veredicto

**FIXES_REQUIRED** — 3 blockers, 17 tests fallidos

**Acciones requeridas**:
1. Resolver duplicados `/coordinator/actas` en nav.config.ts
2. Arreglar mock AuthStore.user como signal
3. Ejecutar `pnpm format`
4. Re-verificar tests

