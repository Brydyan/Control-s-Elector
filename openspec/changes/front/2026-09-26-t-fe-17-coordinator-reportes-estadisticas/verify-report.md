# Verificación T-FE-17: Centro de Reportes y Estadísticas de Coordinación

**Rama**: brydyan/sc-370/t-fe-17-coordinator-centro-de-reportes-y  
**Ronda 3 HEAD**: (post fixes-required-r2)  
**Fecha**: 2026-11-05  
**Ronda**: 3 (Post-Fixes R2)

---

## Baseline — Compuertas Ejecutadas (Ronda 3)

| Compuerta | Comando | Resultado | Status |
|-----------|---------|-----------|--------|
| Lint | `pnpm lint` | exit 0 | ✅ PASS |
| Format | `pnpm format:check` | exit 0 | ✅ PASS |
| Tests | `pnpm test:ci --run` | exit 1, 5 FAIL / 576 PASS | ❌ FAIL |
| Build | `pnpm build` | Skipped (tests ROJO) | ⏸️ DEFER |
| Coverage | `pnpm test:coverage` | Skipped (tests ROJO) | ⏸️ DEFER |

**Progreso respecto a Ronda 2**:
- Ronda 2: 12 tests FAIL (ProxyZone)
- Ronda 3: 5 tests FAIL (nuevos defectos expuestos)
- ✅ ProxyZone resuelto (BrowserAnimationsModule importado correctamente)

---

## Hallazgos Críticos (Ronda 3)

### CRITICAL 1: Dark Theme Token Missing

**Test falla**:
```
FAIL src/theme/primeng-theme.spec.ts:134
should declare the same semantic tokens in both schemes
```

**Síntoma**:
```
AssertionError: --color-surface-default declared in :root but missing in .app-dark
```

**Archivo**: `src/theme/tokens.scss`

**Causa raíz**: Token `--color-surface-default` fue declarado en `:root` (light scheme) pero no en `.app-dark` (dark scheme).

**Línea aproximada**: ~302 en dark scheme block

---

### CRITICAL 2: fakeAsync Decorators Missing (3 tests)

**Tests fallan**:
```
FAIL src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts
  × should calculate actas per hour correctly from timestamps
    ReferenceError: tick is not defined
  × should show error state when metrics fail to load
    ReferenceError: tick is not defined
  × should unsubscribe from observables on ngOnDestroy
    ReferenceError: tick is not defined
```

**Causa raíz**: Tres tests usan `tick()` (async time manipulation) pero NO están envueltos con `fakeAsync()` decorator.

**Líneas aproximadas**: 154, 241, 254 en coordinator-reportes.page.spec.ts

**Evidencia**:
```typescript
// INCORRECTO
it('should calculate actas per hour...', () => {
  // ...
  tick();  // ❌ tick() existe SÓ dentro de fakeAsync
});

// CORRECTO
it('should calculate actas per hour...', fakeAsync(() => {
  // ...
  tick();  // ✅ Ahora tick() está disponible
}));
```

---

### CRITICAL 3: Input Validation Not Enforced

**Test falla**:
```
FAIL src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts:175
should reject invalid actaId (negative, zero, null) before HTTP call
```

**Síntoma**:
```
Error: Expected function to throw an error but it did not throw.
```

**Archivo**: `frontend/app/src/app/features/coordinator/data/coordinator.api.ts`

**Causa raíz**: Método `descargarExpedientePdf(actaId)` no valida que `actaId > 0` antes de hacer la llamada HTTP.

**Test expectativa** (approx línea 175):
```typescript
expect(() => {
  coordinatorService.descargarExpedientePdf(-1);
}).toThrowError('Invalid actaId');
```

**Código actual** (NO valida):
```typescript
descargarExpedientePdf(actaId: number): Observable<Blob> {
  // ❌ NO hay guard para actaId <= 0
  return this.http.get<Blob>(
    `/api/v1/coordinator/actas/${actaId}/expediente-pdf`,
    { responseType: 'blob' as 'json' }
  );
}
```

---

## Mutaciones M1 y M2

**Estado**: NO EJECUTADAS

**Razón**: Baseline en ROJO (5 tests fallando). Política: solo ejecutar mutaciones si baseline VERDE.

---

## Veredicto

**🔴 REWORK REQUERIDO — RONDA 3**

**Blockers**:
1. ❌ **CRITICAL 1**: Dark theme token missing en `.app-dark`
2. ❌ **CRITICAL 2**: 3 tests sin `fakeAsync()` decorator (tick undefined)
3. ❌ **CRITICAL 3**: Validación de actaId no implementada

**Status de R2 fixes**:
- ✅ ProxyZone: Resuelto (BrowserAnimationsModule importado)
- ✅ Raw hex colours: Aparentemente resuelto (no reportados en token-guard)
- 🟡 Theme tokens `--color-surface-default`, `--color-status-danger-strong`: Parcialmente (C1 nuevo: falta en dark scheme)

**Antes de Ronda 4**:
- Cerrar C1: Declarar tokens en dark scheme
- Cerrar C2: Envolver 3 tests con `fakeAsync()`
- Cerrar C3: Agregar validación `actaId > 0` en API
- Re-ejecutar: `pnpm test:ci --run` (esperar exit 0, 581 tests PASS)
- Luego: Ejecutar mutaciones M1 y M2
