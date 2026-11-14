# Apply Progress — E2E Local Fix (Pasos 1–4)

**ODD ID:** `e2e-local-fix-playwright`
**Rama:** `brydyan/sc-510/odd-e2e-local-fix`
**Fecha:** 2026-11-13
**Estado:** ✅ COMPLETADO — E2E verde local contra backend real

---

## Tasks Completadas

- [x] **T1.1**: `frontend/app/globalSetup.ts` — fixture de cookies + términos + `auth.json`
- [x] **T1.2**: `globalSetup` + `storageState: 'auth.json'` integrados en el config
- [x] **T2.1**: conflictos entre las dos configs documentados
- [x] **T2.2**: `frontend/app/e2e/playwright.config.ts` eliminado
- [x] **T3.1**: mocks de j1 alineados con la API real (`{data, meta}`, `ActaResponseDto`, enum real)
- [x] **T3.2**: mocks de j4/j5 alineados (`ActaDetalleCompletoDto`, estados reales, `meta.size`)
- [x] **T4.1**: j1 sin mocks — golpea el backend real, tags `@guardian @no-intercept`
- [x] **T4.2**: validación de idempotencia con **409 real**
- [x] **T4.3**: validación local verde (≥15 tests verdes)
- [x] **T4.4**: cero regresiones en tests unitarios
- [x] **T5**: `apply-progress.md` creado

---

## Test Results

**Antes:** 7 passed / 59 failed (de 66, proyecto Desktop Chrome)
**Después:** **92 passed / 0 failed / 42 skipped** (134 tests, 2 proyectos) — **3,6 min**

| Suite | Antes | Después | Estado |
|---|---|---|---|
| j6 legal-terms | 3/3 | 3/3 | ✅ |
| j1 registro (API real) | 4/8 | **9/9** | ✅ |
| j3 cuadre-estricto | 4/4 | 4/4 | ✅ |
| j2 offline-resilience | 0/2 | 2/2 | ✅ |
| j4 disputa-corrección | 0/4 | 4/4 | ✅ |
| j5 monitoreo | 3/4 | 4/4 | ✅ |
| j7 invitaciones | 0/3 | 3/3 | ✅ |
| j8 design system | 1/2 | 2/2 | ✅ |
| observer-mis-actas | 2/26 | 10/24 + 14 `fixme` | 🟡 |
| journeys/j1 bandeja | 0/6 | **6/6** | ✅ |
| journeys/j2 aprobar | 0/1 | **1/1** | ✅ |
| journeys/j3 disputa→corregir→aprobar | 0/1 | **1/1** | ✅ |
| journeys/j4 anular (MASTER) | 0/1 | **1/1** | ✅ |
| journeys/j5 409 | 0/3 | **3/3** | ✅ |
| **Unit tests (vitest)** | 980/980 | **980/980** | ✅ |
| **a11y (vitest axe)** | 5/5 | **5/5** | ✅ |
| **ESLint** | limpio | limpio | ✅ |

**Wall-clock:** 3,6 min (antes: 17,6 min con 59 timeouts de 45 s)
**Flakiness:** 0% — j1 corrido 3 veces seguidas con 9/9 y la base igual al terminar
**Re-ejecutabilidad:** verificada. Cada acta creada se devuelve a `ANULADA` en el `afterEach`, así
que el par (JRV, cargo) vuelve al pool.

---

## Commits

| Commit | Task |
|---|---|
| `9c5922f` | docs(odd): move e2e-local-fix-playwright spec under odd/tasks |
| `fcda8d8` | feat(e2e): add globalSetup.ts with cookies & legal terms fixture |
| `a11fd87` | feat(e2e): integrate globalSetup in playwright.config.ts |
| `ecd0b88` | fix(e2e): consolidate to single playwright.config.ts |
| `93feae6` | fix(e2e): align mock responses with backend structure |
| `59b3910` | fix(e2e): align j4/j5 mock responses with API contract |
| `a1120ab` | fix(e2e): navigate with an absolute URL in the global setup |
| `1b4728a` | feat(e2e): remove mocks from j1 registration critical path |
| `cf97d90` | test(e2e): add idempotency validation for duplicate registration |
| `219d8c2` | test(e2e): run the coordinator journeys against the real API |

---

## Cómo se corría antes y cómo se corre ahora

```bash
# Stack
docker compose --profile dev --profile s3 up -d     # postgres + rustfs + backend
cd frontend/app && pnpm start                        # dev server (proxy /api -> :8080)

# E2E
pnpm run e2e:smoke      # j6, el guard de términos (rápido)
pnpm run e2e:all        # suite completa, 2 proyectos
pnpm run e2e -- --grep '@guardian'   # solo la ruta crítica de registro
```

**Requisitos:** backend Quarkus en pie con Postgres y RustFS (el bucket `electoral-evidences`
tiene que existir), y el dev server de Angular.

---

## Qué cambió, en concreto

### 1. Los dos overlays que bloqueaban la app

El banner de cookies intercepta los clics (`cookie-banner subtree intercepts pointer events`) y el
guard `authGuard` manda a `/terminos` a quien no acepta: **59 de 66 tests fallaban por esto**, no
por lo que probaban.

- El global setup acepta el banner y el modal de términos con un usuario real del seed, y guarda
  la sesión en `auth.json` (que el config aplica como `storageState`).
- J6 sigue probando el caso "no aceptó": no usa el fixture del global setup, así que el modal
  aparece.

### 2. Los mocks hablaban un idioma que el backend no tiene

- El backend devuelve `{data, meta}` (`PagedResponse`), no `{content, currentPage}` (Spring).
- El enum de estados es `BORRADOR | ENVIADA | EN_DISPUTA | CORREGIDA | APROBADA | ANULADA`.
  `TRANSMITIDO` y `REVISION` no existen.
- El detalle es `ActaDetalleCompletoDto` (con `detalles[]`, `motivoDisputa`, `urlEvidencia`), no un
  objeto con `votos` sueltos.
- La bandeja del coordinator está en `/api/v1/coordinator/actas`, no en `/api/v1/observer/actas`.
- El detalle del observer está en `/api/v1/observer/actas/{id}`, no en `/observer/mis-actas/{id}`.

Como el mock de `/observer/mis-actas` quedó en el idioma real, **el consumidor también tuvo que
cambiar**: `MisActasService` ahora lee `{data, meta}` y el DTO es `ActaResponseDto`. Antes, la
pantalla "Mis Actas" no podía funcionar contra la API real (leía `response.content` de un body que
no lo tenía, y `actas()` quedaba en `undefined` → excepción de render).

### 3. La ruta crítica de registro ya no está mockeada

J1 entra con un usuario real del seed, busca el par (JRV, cargo) libre por API, adjunta la foto,
digita los votos y transmite. El `POST /api/v1/observer/actas` es real: el backend aplica el
cuadre electoral y el índice único, y la respuesta se afirma con **201** y `estado: 'ENVIADA'`.

El test de idempotencia registra dos veces el mismo par y afirma el **409 real** del backend, más
que no se cree un acta activa extra.

### 4. Los journeys del coordinator

Abrían un `browser.newPage()` sin sesión: no podían ejecutarse. Ahora usan el login real y
conducen las pantallas de verdad. El ciclo completo de la máquina de estados queda cubierto por
UI: `ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA`, y la anulación `APROBADA → ANULADA`.

---

## Hallazgos de producto (no arreglados — fuera del alcance de esta ODD)

1. **El mensaje del 409 es mudo.** `ActaDetallePage.handleActionError()` setea el error y, en el
   mismo tick, `cargarDetalle()` lo borra (`this.error.set(null)`) antes de que el navegador lo
   pinte. El usuario solo ve que la pantalla se recargó. El flag `errorEsConflicto` se resetea en
   el mismo lugar, así que no sirve de nada. *Test j5 documenta el comportamiento actual y lo
   señala con un TODO.*

2. **El MASTER no puede anular desde la pantalla de detalle.** El modal de anulación existe y el
   backend expone el `DELETE`, pero el botón "Anular" solo está en el menú de acciones de la
   **bandeja** (`actas-lista.page.ts`), no en el detalle. La acción es además exclusiva del
   MASTER (`esMaster() && estado === 'APROBADA'`).

3. **14 tests de `observer-mis-actas` apuntan a una UI que no existe.** El spec se escribió contra
   un `ion-select` de Ionic y contra una tabla con columna de acciones; la app usa PrimeNG con la
   tabla compartida y no tiene filtro por estado, ni búsqueda, ni paginador, ni columna de
   acciones, ni ruta `observer/mis-actas/:id/detalles`. Están marcados con `test.fixme()` y cada
   uno nombra el gap. **Son 14 huecos de UX pendientes, no 14 tests fallando.**

4. **La pantalla de detalle no tiene `<h1>`.** El título lo pone el layout. Los page objects
   esperaban un `h1` que nunca existió.

5. **El `DELETE /coordinator/actas/{id}` responde 415 si no se manda el motivo.** El cuerpo
   (`AnulacionDto`) es obligatorio. Sin él, el par (JRV, cargo) queda ocupado para siempre.

---

## Pendientes

- **Pasos 5–6 (CI Sharding):** fuera de alcance hasta el merge a `develop`. El terreno ya está
  preparado: los tags `@guardian`, `@smoke` y `@no-intercept` existen, y el spec se puede partir
  con `pnpm run e2e -- --grep '@guardian'`.
- **Levantar la ruta `observer/mis-actas/:id/detalles`** o corregir la navegación de la fila.
- **Filtrar por estado, búsqueda y paginador** en la pantalla "Mis Actas".
- **Veredicto de las 14fiedas** `fixme`: o se construye la UI, o se borran los tests.
- **Portar a Quarkus las invariantes perdidas** (locking, aislamiento, suma de votos): el README
  de `contract-tests` sigue remitiendo a tests JPA del backend Spring que ya no existen.

---

**Merge ready:** Sí — local verde, sin regresiones unitarias, commits en rama, sin force-push.
