# Archive Report — T-FE-13: Modo "Foto Primero" (Capture & Run) y Cotas Electorales

**Status**: ARCHIVED  
**Date**: 2026-11-01  
**Change**: `front/2026-09-25-t-fe-13-capture-and-run-foto-primero`  
**Archive Location**: `openspec/changes/archive/2026-09-25-t-fe-13-capture-and-run-foto-primero/`  
**Verdict**: ✅ APPROVED (0 bloqueadores, Ronda 2 PASS)  
**Branch**: `brydyan/sc-361/t-fe-13-modo-foto-primero-capture-run-y-cotas`  
**HEAD**: `0d51b68` (Merge origin/develop: resolver conflicto en registrar-acta.page.ts)

---

## SDD Cycle Summary

Ciclo completo de SDD para T-FE-13: implementación del flujo "Foto Primero" en el registro de actas electorales, con cotas de sufragantes y validación de cuadre estricto 1:1.

| Fase | Estado | Evidencia |
|------|--------|-----------|
| **Proposal** | ✅ | `proposal.md`: Flujo Foto Primero (pasos reordenados), cotas (350 adv, 500 bloqueo), cuadre 1:1 estricto, almacenamiento IndexedDB sin localStorage |
| **Design** | ✅ | `design.md`: D1 (wizard IMAGEN first), D2 (cotas y alertas), D3 (invariante matemático), D4 (persistencia IndexedDB) |
| **Tasks** | ✅ | `tasks.md`: 11 tareas (A.1, A.2, B.1-B.3, C.1, D.1-D.2, V.1-V.3) |
| **Implementation** | ✅ | `apply-progress.md` (Ronda 1): Builder cerró 11/11 tareas, 468 tests totales (+25 nuevos), mutaciones verificadas |
| **Verification** | ✅ APPROVED | `verify-report.md` (Ronda 2): 471 tests, 30 tests T-FE-13 encontrados, mutaciones V.1/V.2/V.3 detectadas, UX/accesibilidad validada, `pnpm lint/format/build/test:ci/check:tokens` exit 0 |

---

## Requisitos Funcionales y Cobertura

| Requisito | Descripción | Estado | Evidencia |
|-----------|-------------|--------|-----------|
| **RF-A.1** | Wizard arranca en paso IMAGEN (Foto Primero) | ✅ | `currentStep = signal<Step>('IMAGEN')`, 4 tests A.1 verificados, mutación V.2 detecta regresión |
| **RF-A.2** | Persistencia de foto en IndexedDB (no localStorage ni Base64) | ✅ | `guardarFotoBorrador()` vía OfflineStorageService, grep localStorage = 0, 6 tests A.2 verificados, mutación V.3 detecta regresión |
| **RF-B.1** | Cotas de sufragantes (advertencia 350, bloqueo 500) | ✅ | `alertaSufragantes` getter + `excedeTopeSufragantes`, botón "Transmitir" disabled si > 500, 4 tests B.1 verificados, mutación V.1 detecta regresión |
| **RF-B.2** | Validación cuadre 1:1 estricto | ✅ | `isChecksumValid: totalVotosComputados === totalSufragantes`, 1 test B.2 verificado |
| **RF-B.3** | Banners visuales de cota | ✅ | CSS con tokens theme, role="alert" (error) / role="status" (warning), 2 tests B.3 verificados |
| **RF-C.1** | Botón Captura Rápida en JrvCargosPage | ✅ | Modal con `p-dialog`, recorre dignidades, guarda foto en IndexedDB con draft único, 4 tests C.1 verificados |

---

## Mutaciones Ejecutadas y Verificadas

Protocolo estricto: respaldo `.bak` → mutación → ejecución → constatación de test caído → restauración → verificación limpia.

### V.1 — Quitar validación > 500 sufragantes

**Mutación**: `TOPE_SUFRAGANTES_CNE = 5000` (línea 58)  
**Tests caídos**: 4
- `T-FE-13 B.1: con 501 sufragantes hay error y el envío queda bloqueado`
- `T-FE-13 B.1: con 2500 sufragantes (error de tipeo) el envío queda bloqueado` ← el descripto en SDD
- `T-FE-13 B.3: el banner de cota se pinta con el nivel correcto`
- `T-FE-13 B.3: la revisión muestra la cota y bloquea "Transmitir Acta"`

**Resultado**: ✅ Detectada. Restaurado desde `.bak`, git status limpio.

### V.2 — Cambiar paso inicial a 'VOTOS'

**Mutación**: `signal<Step>('VOTOS')` (línea 156)  
**Tests caídos**: 4
- `T-FE-13 A.1: el wizard arranca en el paso IMAGEN (Foto Primero)`
- `T-FE-13 A.1: tras capturar la foto aparece "Continuar a Digitación de Votos"`
- `T-FE-13 A.1: "Digitar votos primero" permite saltar la foto`
- `should have file input with accept="image/*" and capture="environment" (S3)`

**Resultado**: ✅ Detectada. Flujo Foto Primero bloqueado correctamente.

### V.3 — Sustituir IndexedDB por localStorage.setItem

**Mutación**: `localStorage.setItem('acta_drafts', JSON.stringify({…}))` en `guardarBorradorOffline()`  
**Tests caídos**: 4
- `T-FE-13 A.2: el borrador NO se escribe en localStorage ni en Base64`
- `T-FE-13 A.2: el autoguardado de los votos NO borra la foto ya guardada`
- `S6: el paso de votos persiste un borrador para no perder lo escrito`
- `S6: al cambiar de paso se guarda el borrador antes de navegar`

**Resultado**: ✅ Detectada. Arquitectura offline/no-localStorage garantizada.

---

## Compuertas Finales (Ronda 2)

```bash
$ cd frontend/app
$ TZ=UTC pnpm lint          → exit 0 ✓ (All files pass linting.)
$ TZ=UTC pnpm format:check  → exit 0 ✓ (All matched files use Prettier code style!)
$ TZ=UTC pnpm build         → exit 0 ✓ (Application bundle generation complete.)
$ TZ=UTC pnpm test:ci       → exit 0 ✓ (54 files / 471 tests)
$ TZ=UTC pnpm check:tokens  → exit 0 ✓ (Token guard: no violations.)
```

**Medición (Ronda 2)**:
- Test Files: 54 passed
- Tests: 471 passed (446 baseline + 25 nuevos)
- Exit codes: todos 0

---

## UX/Accesibilidad Verificada

**registrar-acta.page.html**:
- ✅ Input `capture="environment"` para captura de cámara en móvil
- ✅ Banner cota con `role="alert"` (error >500) y `role="status"` (warning >350)
- ✅ Checksum con `aria-invalid` cuando no cuadra
- ✅ Inputs de votos con `aria-label`
- ✅ Preview con `alt`

**jrv-cargos.page.html** (Captura Rápida):
- ✅ Modal con focus trap (`p-dialog` + `[modal]="true"`)
- ✅ Escape cierra modal (`[closable]="true"`)
- ✅ Input `capture="environment"` por dignidad
- ✅ Estado visual (check/cámara) para foto capturada

---

## Archivos Modificados (9 archivos)

```
M frontend/app/src/app/core/offline/offline-storage.service.spec.ts
M frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.html
M frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.scss
M frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.spec.ts
M frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.ts
M frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.html
M frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.scss
M frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts
M frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.spec.ts
```

---

## Hallazgos Fuera de Scope

1. **`ActaStorageService` quedó sin consumidores**: Removido del flujo por prohibición de localStorage (D4). No se borró — decisión del equipo para ciclo aparte.

2. **Rama detrás de develop**: 5 commits atrás. Merge futuro tocará `registrar-acta.page.ts` — requiere resolución manual contra cambios de esta fase.

3. **`fake-indexeddb` no preserva Blob en tests**: Manejado — asserts verifican "no es string/Base64" en lugar de `instanceof Blob`.

4. **`pnpm test` cuelga en watch mode**: Mitigation — `pnpm test:ci` ejecuta exitosamente.

---

## Conclusión

T-FE-13 completa 100% de requisitos:
- ✅ Flujo Foto Primero operacional
- ✅ Cotas de sufragantes (350 adv, 500 bloqueo) con UI clara
- ✅ Cuadre 1:1 estricto validado
- ✅ IndexedDB sin localStorage ni Base64
- ✅ Captura Rápida modal en JrvCargosPage
- ✅ 471 tests pasando (30 nuevos de T-FE-13)
- ✅ Mutaciones V.1/V.2/V.3 detectadas
- ✅ Compuertas lint/format/build/test/tokens exit 0
- ✅ UX/accesibilidad validada

**Verdict**: APPROVED. Ready for merge to develop.

---

**Archivado por**: sdd-verify (Ronda 2)  
**Timestamp**: 2026-11-01 22:45 UTC  
**Rama**: `brydyan/sc-361/t-fe-13-modo-foto-primero-capture-run-y-cotas`
