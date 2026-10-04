# Archive Report — 2026-09-25-fe-vitest-ionic-cleanup-race: Fix Condición de Carrera Vitest + Ionic y Compuerta Estricta CI

**Status**: ARCHIVED  
**Date**: 2026-10-04  
**Change**: `front/2026-09-25-fe-vitest-ionic-cleanup-race`  
**Archive Location**: `openspec/changes/archive/2026-09-25-fe-vitest-ionic-cleanup-race/`  
**Verdict**: APPROVED (0 bloqueadores, 0 warnings en Ronda 4)  
**HEAD**: `161ef78cd6d5234744eb35c8a08e9589d1b62224`  

---

## SDD Cycle Summary

Este archivo documenta el ciclo completo de SDD para el fix `2026-09-25-fe-vitest-ionic-cleanup-race`. El problema surgió a raíz de un fallo de carrera asíncrona en el teardown de Vitest con Web Components de `@ionic/core` que provocó la inclusión provisional de `continue-on-error: true` en CI (`FRONTEND_ISSUES.md`). A través del ciclo SDD se diseñó la solución arquitectónica, se implementaron los stubs y aislaciones necesarias, se validaron compuertas y mutaciones empíricas, y se eliminó toda deuda técnica.

| Fase | Estado | Evidencia Clave |
|------|--------|-----------------|
| **Proposal** | ✅ Completa | `proposal.md`: Causa raíz identificada, compuerta estricta requerida, eliminación de `continue-on-error` y `FRONTEND_ISSUES.md`. |
| **Specification** | ✅ Completa | `specs/frontend/spec.md`: 3 requisitos funcionales (RF-TEST-01..03) + 3 escenarios BDD. Suite actualizada a (≥ 41 suites, ≥ 235 tests). |
| **Design** | ✅ Completa | `design.md`: D1 (override template `'<div></div>'` y stubs para neutralizar `<ion-router-outlet>`), D2 (`isolate: true` y deps inline), D3 (eliminación de bypass en CI). |
| **Tasks** | ✅ Completa | `tasks.md`: Checklist A, B, C ejecutado 100%. Protocolos de mutación C.1 y C.2 completados. |
| **Implementation** | ✅ Completa | `apply-progress.md`: 4 rondas de ajuste, resolución de CRITICAL 1, activación de `isolate: true` y sincronización con develop (`cec61ad`, `0ac50de`). |
| **Verification** | ✅ APPROVED | `verify-report.md` (Ronda 4): 0 bloqueadores, 0 warnings. 42/42 suites y 240/240 tests pasando en verde, lint exit 0, build exit 0. Mutaciones verificadas. |

---

## Cumplimiento de Requisitos y Escenarios

**Objetivo**: 3 requisitos y 3 escenarios evaluados empíricamente de forma independiente.

| Requisito / Escenario | Descripción | Estado | Evidencia de Medición |
|---|---|:---:|---|
| **RF-TEST-01** / **Escenario 1** | Ejecución limpia y determinista de tests en modo CI (`pnpm test:ci`) | ✅ CUMPLE | `cd frontend/app && pnpm test:ci`: 42/42 suites, 240/240 tests en verde, 0 unhandled rejections, 0 errores de módulo cerrado, duración ~5.20s, `exit=0`. |
| **RF-TEST-02** / **Escenario 2** | Aislamiento del componente raíz `AppComponent` | ✅ CUMPLE | `app.component.spec.ts` utiliza `TestBed.overrideComponent(AppComponent, { set: { imports: [], template: '<div></div>', schemas: [CUSTOM_ELEMENTS_SCHEMA] } })`. No se monta `<ion-router-outlet>` ni se invocan dynamic imports rezagados de Stencil (`p-Sh0ICmPV.js`). |
| **RF-TEST-03** / **Escenario 3** | Compuerta dura en CI sin excepciones | ✅ CUMPLE | `grep -n "continue-on-error" .github/workflows/ci.yml` retorna vacío. Si un test falla (mutación C.2 en `button.component.spec.ts`), Vitest retorna `exit=1` y detiene el flujo de CI. |

---

## Protocolo de Mutaciones Ejecutadas y Verificadas

Se aplicó el protocolo estricto: respaldo con `.bak`, introducción de mutación, ejecución de test suite, constatación de nombre de test caído y salida no-cero, y restauración con verificación de estado limpio.

1. **Mutación C.1 (`app.component.spec.ts:23`)**:
   - Inversión de aserción: `expect(app).toBeFalsy();`
   - **Resultado**: Test `FAIL  src/app/app.component.spec.ts > AppComponent > should create the app`. Salida `exit=1`. Cero unhandled rejections durante la falla.
   - **Garantía probada**: El test de `AppComponent` detecta regresiones efectivas y no es meramente decorativo.

2. **Mutación C.2 (`button.component.spec.ts:19`)**:
   - Inyección de fallo duro: `expect(true).toBe(false);`
   - **Resultado**: Test `FAIL  src/app/shared/ui/button/button.component.spec.ts > ButtonComponent > should create`. Vitest retorna `exit=1`.
   - **Garantía probada**: CI no enmascara fallos; cualquier regresión real en frontend interrumpe el pipeline.

---

## Métricas Finales de Compuertas

- **Pruebas unitarias**: `cd frontend/app && pnpm test:ci` → **42 passed (42)** suites, **240 passed (240)** tests, **0 errores**, `exit 0`.
- **Linter**: `cd frontend/app && pnpm lint` → `All files pass linting`, `exit 0`.
- **Build de producción**: `cd frontend/app && pnpm build` → `Application bundle generation complete`, `exit 0`.
- **Compuerta CI**: `grep -n "continue-on-error" .github/workflows/ci.yml` → 0 coincidencias.
- **Deuda técnica saneada**: `FRONTEND_ISSUES.md` eliminado exitosamente.

---

## Decisiones Técnicas y Solución Definitiva

1. **Neutralización del DOM en `AppComponent` (D1)**:
   `AppComponent` es solo un contenedor estructural `<ion-app><ion-router-outlet></ion-router-outlet></ion-app>`. En la prueba unitaria, la hidratación de `<ion-router-outlet>` disparaba `createSwipeBackGesture` en `p-Sh0ICmPV.js` de Stencil tras concluir el test. Al sobreescribir la plantilla a `template: '<div></div>'` y asignar `CUSTOM_ELEMENTS_SCHEMA`, se elimina la carga asíncrona de Web Components pesados sin alterar el código de producción.

2. **Aislamiento en `vite.config.ts` y defensa en teardown (D2)**:
   Se mantiene `isolate: true` en `vite.config.ts`, asegurando que el estado de singletons y DI no se contamine entre archivos. Además, en `src/test-setup.ts` se envuelve `addIcons` con `try-catch` como salvaguarda defensiva contra invocaciones residuales en el cierre de workers.

3. **Restauración de compuertas estrictas (D3)**:
   Se eliminó `continue-on-error: true` del workflow de GitHub Actions, restableciendo la seguridad de que cualquier fallo en frontend bloquea el pull request. Se eliminó el documento temporal `FRONTEND_ISSUES.md`.

---

## Fusión de Especificaciones (Delta → Main)

- **Origen**: `openspec/changes/front/2026-09-25-fe-vitest-ionic-cleanup-race/specs/frontend/spec.md`
- **Destino**: `openspec/specs/frontend/spec.md`
- **Contenido incorporado**:
  - Requisitos RF-TEST-01, RF-TEST-02, RF-TEST-03.
  - Escenarios 1, 2 y 3.
  - Garantías G1, G2 y G3 del subsistema de pruebas frontend y CI.

---

## Archivo del Cambio

- **Ubicación final**: `openspec/changes/archive/2026-09-25-fe-vitest-ionic-cleanup-race/`
- **Operación**: Mover directorio con preservación íntegra de artefactos (`proposal.md`, `design.md`, `specs/`, `tasks.md`, `apply-progress.md`, `fixes-required.md`, `verify-report.md`, `archive-report.md`).
- **Estado**: COMPLETADO Y LISTO PARA PRODUCCIÓN.
