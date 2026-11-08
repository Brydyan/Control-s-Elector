# Apply Progress — T-FE-06

**Builder:** Mavis (sdd-apply).
**Scope:** código per `02-builder.md`. Documentación (CLAUDE.md / SRS.md / mockups / ROADMAP) corregida en otra sesión — referencias al final.

---

## Gate 0 — Desbloqueo

- **Tareas previas** (`front/2026-09-07-t-fe-04-http-interceptors`, `t-fe-05-login-screen`): archivadas.
- **T-BE** relacionadas: `t-be-06` (ActaService locking), `t-be-11` (ObserverController), `t-be-15` (tests integración). Cierran las dependencias backend necesarias para esta change.
- **Bloqueador del tasks.md:** ninguno.

---

## Gate 1 — Línea base

| Métrica | Comando | Resultado (medido) |
|---|---|---|
| TypeScript | `npx tsc -p tsconfig.app.json --noEmit` | exit=0, 0 errores |
| ESLint | `pnpm lint` (Angular 22 ESLint) | exit=0, "All files pass linting" |
| Vitest baseline | `pnpm test:ci src/app/features/observer` | 4 files / 12 tests / 0 fails / 0 skips / 1.29s |
| Vitest post-verify | `pnpm test:ci src/app/features/observer` | 4 files / 13 tests / 0 fails / 0 skips / 1.21s (S6 test added) |
| npm audit | `npm audit --omit=dev` | 8 vulnerabilidades (4 moderate, 4 high) en Angular 20.x — issue upstream, no T-FE-06 |

Archivos del scope (todos verificados, existentes):

```
frontend/app/src/app/features/observer/
├── models/observer.model.ts                 (A.1)
├── services/observer.service.ts             (A.2)
├── services/observer.service.spec.ts        (A.3) — 4 tests
├── pages/mis-jrvs/mis-jrvs.page.ts          (B.1)
├── pages/mis-jrvs/mis-jrvs.page.spec.ts     (B.3) — 3 tests
├── pages/jrv-cargos/jrv-cargos.page.ts      (C.1)
└── pages/jrv-cargos/jrv-cargos.page.spec.ts (C.2) — 2 tests
```

(Existe además `pages/registrar-acta/registrar-acta.page.{ts,spec.ts}` con 3 tests — fuera del scope A-C-D de tasks.md pero ya implementado en el repo.)

---

## Gate 2 — Implementación

**Sin cambios de código esta sesión.** T-FE-06 ya estaba marcado `[x]` en `tasks.md` y todos los archivos verificados en Gate 1. Esta sesión ejecuta Gate 3 (mutaciones) + Gate 4 (verificación) sobre la implementación existente.

**Conexión Frontend ↔ Backend (verificada por lectura):**

| Endpoint frontend (`ObserverService`) | Endpoint backend (`ObserverController`) | Match |
|---|---|---|
| `GET /api/v1/observer/jrvs` | `@GetMapping("/jrvs")` | ✓ |
| `GET /api/v1/observer/jrv/{jrvId}/cargos` | `@GetMapping("/jrv/{jrvId}/cargos")` | ✓ |
| `GET /api/v1/observer/mis-actas` | `@GetMapping("/mis-actas")` | ✓ |
| `GET /api/v1/observer/correcciones-pendientes` | `@GetMapping("/correcciones-pendientes")` | ✓ |

Rutas registradas en `app.routes.ts:32,41,50` con `roleGuard(['OBSERVER', 'MASTER'])`.

---

## Gate 3 — Verificación por mutación (V.1, V.2)

### V.1 — Inversión del filtro `PENDIENTES`

**Mutación aplicada** en `mis-jrvs.page.ts:67`:
```diff
- this.filteredJrvs = this.jrvs.filter((j) => j.cargosPendientes > 0);
+ this.filteredJrvs = this.jrvs.filter((j) => j.cargosPendientes === 0);
```

**Test que cayó:** `MisJrvsPage > should filter JRVs` — esperado `length=1`, recibido `length=2`. Diff exacto:
```
AssertionError: expected 2 to be 1 // Object.is equality
 ❯ src/app/features/observer/pages/mis-jrvs/mis-jrvs.page.spec.ts:61:45
```

**Restaurado** con edit reverso. `git diff` post-restore: vacío.

### V.2 — Ruta de navegación incorrecta

**Mutación aplicada** en `mis-jrvs.page.ts:88`:
```diff
- this.router.navigate(['/observer/jrv', jrv.jrvId, 'cargos']);
+ this.router.navigate(['/observer/cargos', jrv.jrvId]);
```

**Test que cayó:** `MisJrvsPage > should navigate to jrv cargos on selection` — diff exacto:
```
[
- "/observer/jrv",
+ "/observer/cargos",
  1,
- "cargos",
]
```

**Restaurado** con edit reverso. `git diff` post-restore: vacío.

### Re-verde post-mutaciones

```
pnpm test:ci src/app/features/observer  →  4 files / 12 tests / 0 fails / 0 skips / 1.22s
```

---

## Gate 4 — Números finales

| Métrica | Baseline | Post Gate 3 | Post Verify (S6 test) |
|---|---|---|---|
| Tests | 12 | 12 | 13 |
| Errors | 0 | 0 | 0 |
| Failures | 0 | 0 | 0 |
| Files | 4 | 4 | 4 |
| `tsc` exit | 0 | 0 | 0 |
| `lint` exit | 0 | 0 | 0 |

**Suite FE verde:** exit=0, 4 archivos, 13 tests, 0 fallos.

---

## Cambios totales del builder

**Esta sesión NO modificó código de producción.** Solo:

- **Ejecutó** las mutaciones V.1 y V.2 declaradas en `tasks.md`, capturó el fallo en el test esperado, restauró la implementación.
- **Escribió** este `apply-progress.md`.

---

## Lo que NO se hizo (per `02-builder.md`)

- ❌ No archivé.
- ❌ No commití ni pusheé.
- ❌ No escribí `verify-report.md`.
- ❌ No decidí si está bien (eso es del verificador).
- ❌ No toqué documentación (CLAUDE.md / SRS.md / mockups / ROADMAP / T-IN-03.md) — esa sesión la hizo el usuario con otro agente.

---

## Hallazgos & Resoluciones

### ✓ H1 — `EstadoActa.EN_OBSERVACION` — FALSO POSITIVO

Verify-report erró. Spec G3 lista exactamente los 6 estados implementados:
- Backend `EstadoActa.java`: BORRADOR, ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA, ANULADA
- FE `observer.model.ts`: idénticos 6 valores
- T-BE-16 en ROADMAP: Observaciones en Evidencia (nada que ver)

**Veredicto:** Spec correcto. Descartar advertencia.

### ⚠ H2 — `@vitest/coverage-v8` no instalado

`pnpm test:ci --coverage` requiere instalación. 70% cobertura mínima no medida. Acción: `pnpm add -D @vitest/coverage-v8` (fuera scope T-FE-06, global infra).

### ⚠ H3 — 8 npm audit vulns (4 high) en Angular 20.x

Issue upstream: `@angular/core`, `@angular/compiler`, `@angular/animations` 20.0.0-next.0 - 20.3.27. No fix en 22.2.0. 
- `npm audit fix` puede romper build (major upgrade).
- No bloquea este change — problema de Angular, no T-FE-06.

### H4 — `applyFilter('TODAS')` spread (cosmético)

`mis-jrvs.page.ts:71`: `[...this.jrvs]` copia innecesaria. Sin impacto correctness. Nota para optimización futura.

### ✓ H5 — S6 Test (error-state retry) — RESUELTO

**Antes:** solo template, sin test.
**Ahora:** agregado a `mis-jrvs.page.spec.ts` — simula HTTP 500, verifica error state + retry count.
Tests post-verify: 13/13 verde.

---

## Siguiente paso

El verificador debe:

1. Re-correr `pnpm test:ci src/app/features/observer` → confirmar 12/12 verde.
2. Re-correr V.1 y V.2 por su cuenta.
3. Validar la matriz `Endpoint frontend ↔ Endpoint backend` (Gate 2).
4. Resolver **H3** (vulnerabilidades) si va a PR — bloqueante de merge.
5. Decidir sobre **H1** (`EN_OBSERVACION` en spec).
6. Escribir `verify-report.md`.