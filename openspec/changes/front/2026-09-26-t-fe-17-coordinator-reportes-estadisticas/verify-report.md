# Verificación T-FE-17: Centro de Reportes y Estadísticas de Coordinación

**Rama**: brydyan/sc-370/t-fe-17-coordinator-centro-de-reportes-y  
**Ronda 2 HEAD**: 78bc0cfe1517962c2d7f24f15e4828b060687f51 (fix(t-fe-17): aplicar fixes-required C1-C3-W1)  
**Fecha**: 2026-11-05  
**Ronda**: 2 (Post-Fixes)

---

## Baseline — Compuertas Ejecutadas (Ronda 2)

| Compuerta | Comando | Resultado | Status |
|-----------|---------|-----------|--------|
| Lint | `pnpm lint` | exit 0 | ✅ PASS |
| Format | `pnpm format:check` | exit 0 | ✅ PASS |
| Tests | `pnpm test:ci --run` | exit 1, 12 FAIL / 569 PASS | ❌ FAIL |
| Build | `pnpm build` | exit 0 | ✅ PASS |
| Coverage | `pnpm test:coverage` | Bloqueado por failures | ⏸️ DEFER |

**Progreso respecto a Ronda 1**:
- Ronda 1: build FAIL (TS2305 Logger), tests FAIL (24 tests)
- Ronda 2: build PASS ✅ (fixes aplicados), tests FAIL (12 tests, nuevo error)

---

## Hallazgos Críticos (Ronda 2)

### CRITICAL 1: ProxyZone Error en Tests

**Síntoma**:
```
Error: Expected to be running in 'ProxyZone', but it was not found.
```

**Archivo**: `src/app/features/coordinator/pages/reportes/coordinator-reportes.page.spec.ts`

**Tests afectados** (12 FAIL):
- ❌ should load metrics on ngOnInit and display dashboard
- ❌ should calculate actas per hour correctly from timestamps
- ❌ should show error state when metrics fail to load
- ❌ should handle 401 Unauthorized during download and show auth message
- ❌ should validate MIME type of downloaded blob and reject non-PDF
- ❌ should revoke Object URL after download to prevent memory leak
- ❌ should unsubscribe from observables on ngOnDestroy
- ❌ should handle 403 Forbidden during download
- ❌ should handle 404 Not Found during download
- (más 3 tests)

**Causa raíz**: TestBed no está configurado con `TestingModule` que soporte zona.js. Tests usan `fakeAsync()` pero la zona Angular (ProxyZone) no está disponible.

**Evidencia**:
```typescript
// El test corre fakeAsync pero TestBed no prepara zona
it('should load metrics...', fakeAsync(() => {
  // Error: zona no está disponible
  tick();
}));
```

**Status de Ronda 1**: C1 (Logger) Y C2 (theme tokens) aparentemente cerrados, pero **C3 (tests con mutaciones) nunca se ejecutó**. Ronda 2 es primera que ejecuta suite completa y expone defecto de configuración.

---

### CRITICAL 2: Raw Colours `#fff` en SCSS

**Archivo**: `src/app/features/coordinator/pages/reportes/coordinator-reportes.page.scss`

**Violaciones encontradas**:
- Línea 34: `color: #fff;` (btn-retry)
- Línea 52: `color: #fff;` (banner-progreso)
- Línea 339: `color: #fff;` (btn-descargar)

**Test que falla**:
```
FAIL src/theme/token-guard.spec.ts > the token guard > should not find a violation in the app
```

**Causa raíz**: Ronda 1 documentó que C3 es "Raw colours (rgba)" pero encontrados son **hexadecimales directos** `#fff`, no rgba. Builder arregló rgba pero no hex colors.

**Regla violada**: "raw-colour — is a raw colour outside the theme layer"

---

### PARTIAL FIX: Theme Tokens `--color-surface-default` y `--color-status-danger-strong`

**Status**: Parcialmente arreglado.

- Línea 3 en SCSS: `background-color: var(--color-surface-default);` aún existe pero **no aparece como violación en token-guard**, lo que implica que:
  - Token SÍ fue declarado en `src/theme/tokens.scss`, O
  - Fue mapeado a otro token existente

**Conclusión**: C2 probablemente cerrado (sin evidencia de error), pero sin confirmación explícita.

---

## Progreso de Fixes (Ronda 1 → Ronda 2)

| Fix ID | Descripción | Ronda 1 | Status Ronda 2 | Evidencia |
|--------|-------------|---------|----------------|-----------|
| **C1** | Logger no importable | CRITICAL | ✅ CERRADO | build exit 0 |
| **C2** | Theme tokens no declarados | CRITICAL | 🟡 PARTIAL | sin error token-guard (aún) |
| **C3** | Raw colours (rgba/hex) | CRITICAL | ❌ INCOMPLETE | 3 x #fff encontrados |
| **W1** | ErrorStateComponent unused | WARNING | ❓ UNKNOWN | Build no reporta warning |
| **NEW: ProxyZone** | Tests zone.js config | — | ❌ CRITICAL | 12 tests fallando |

---

## Mutaciones M1 y M2

**Estado**: NO EJECUTADAS

**Razón**: Baseline está en ROJO (tests fallando). Política de verificación exige baseline VERDE antes de mutar.

**Requerimiento para ejecutar**:
```
✅ pnpm lint: exit 0
✅ pnpm format:check: exit 0
✅ pnpm test:ci --run: exit 0 (TODO: 581 tests PASS)
✅ pnpm build: exit 0
✅ Cobertura: ≥70% (medido)
```

Actualmente: 5/5 compuertas. Falta **1 de 5** (tests).

---

## Veredicto

**🔴 REWORK REQUERIDO — RONDA 2**

**Blockers encontrados**:
1. ❌ **CRITICAL: ProxyZone error** — 12 tests no ejecutan (zona.js config)
2. ❌ **CRITICAL: Raw hex colours** — 3 violations en token-guard (#fff)
3. 🟡 **PARTIAL: Theme tokens** — Parcialmente cerrado (sin error, pero sin confirmación)

**Antes de siguiente verificación**:
- Resolver ProxyZone: configurar TestBed con zona.js
- Resolver hex colours: mapear a theme tokens
- Confirmar C2: verificar tokens declarados en theme
- Re-ejecutar: `pnpm test:ci --run` (esperar exit 0, 581 tests PASS)
- Luego: ejecutar mutaciones M1 y M2

**Status**: Progreso respecto a Ronda 1 (build ahora pasa), pero nuevos bloqueadores expuestos por ejecución real de tests.
