# Apply Progress — ODD: Master Dashboard + Routing Fixes

**Implementador:** agente Mavis (modo ODD / Implementation Routing)
**Change:** `odd/tasks/2026-11-07-master-dashboard-routing-fixes.md`
**Rama:** `brydyan/sc-478/odd-cors-license-thirdparty-jacoco` (sin push)
**Fecha:** 2026-11-07

> Este archivo lo escribe el implementador, no el verificador. **No** decide si el trabajo está
> bien: eso lo hace otro, con contexto limpio.

---

## A — Números medidos

### A.1 — Línea base (antes de tocar nada)

```
frontend test   pnpm test:ci   68 suites / 651 tests / 0 fallos   exit 0
frontend lint   pnpm lint      0 errores                          exit 0
frontend fmt    pnpm format:check  0 errores                      exit 0
frontend build  pnpm build     exit 0  (818.87 kB initial)
```

### A.2 — Resultado al terminar

```
frontend test   pnpm test:ci   69 suites / 664 tests / 0 fallos   exit 0
frontend lint   pnpm lint      0 errores                          exit 0
frontend fmt    pnpm format:check  0 errores                      exit 0
frontend build  pnpm build     exit 0  (818.87 kB initial)

+1 suite, +13 tests:
  app.routes.spec.ts                        5 -> 9    (+4)
  dashboard.api.spec.ts                     7 -> 11   (+4)
  progreso-cantones.rendering.spec.ts      0 -> 5    (+5, suite nueva)
```

---

## B — Diagnóstico: los tres issues ya estaban arreglados

**El task se escribió contra un estado obsoleto.** Los tres issues los arregló
`bdfa452` (PR #175, *"fix(frontend): align navigation routes, observer ids, and dashboard canonical
endpoints"*) el 2026-11-07 a las **14:59**, siete horas antes de que el task se escribiera
(21:28). Ese commit está en esta rama.

### B.1 — Issue 1: "No se pudieron cargar los resultados"

**Causa raíz real:** el frontend pedía endpoints que **no existen** en el backend.

```
antes (bdfa452⁻):  GET /api/v1/dashboard/resultados-detallados     -> 404
                    GET /api/v1/dashboard/progreso-detallado      -> 404
despues:            GET /api/v1/dashboard/resultados              -> 200
                    GET /api/v1/dashboard/progreso               -> 200
```

El `DashboardResource` expone `/resumen`, `/resultados`, `/progreso`, `/novedades`. No hay
`-detallados`. El 404 caía en el `error` handler de la página, que es exactamente el mensaje del
síntoma (*"No se pudieron cargar los resultados. Por favor intenta nuevamente."*, con el título
por defecto del `ErrorStateComponent`, *"Ocurrió un error"*).

El fix de #175 fue consumir los endpoints canónicos y enriquecer en el cliente
(`getResultadosDetalles` calcula `votosValidos`, `porcentaje` y `estado` a partir de `/resultados`).

**Verificado que el contrato actual está sano:**

| | Backend | Frontend | |
|---|---|---|---|
| `ResumenActasDto` | `enviadas, enDisputa, corregidas, aprobadas, totalActivas` | `ResumenActas` | ✓ |
| `ResultadoCargoDto` | `cargoId, cargoNombre, candidatos` | `ResultadoCargo` | ✓ |
| `ResultadoCandidatoDto` | `candidatoId, candidatoNombre, partidoNombre, votos` | `ResultadoCandidato` | ✓ |
| `ProgresoCantonDto` | `cantonId, cantonNombre, totalJrvs, jrvsConActa, porcentaje` | `ProgresoCanton` | ✓ |
| `NovedadDto` | 10 campos | `Novedad` | ✓ |

**Y que `environment.apiUrl` no es el culpable.** Es `'/api/v1'` (relativa) en dev y prod, a
propósito: el dev-server proxea `/api` a `:8080` (`proxy.conf.json`) y en producción nginx hace
proxy de `/api` al backend. Es la única forma de que la cookie httpOnly del refresh funcione
igual. **El fix propuesto por el task la habría roto.**

### B.2 — Issue 2: "Mis Actas rebota al dashboard"

`/observer/mis-actas` no estaba registrada; la ruta comodin `**` la mandaba al dashboard. Ahora
está registrada (`roles: ['OBSERVER','MASTER']`) y renderiza el `PlaceholderPage`. **El fix
propuesto por el task (`/master/mis-actas`) habría duplicado la ruta existente**, no la habría
arreglado.

### B.3 — Issue 3: "Catálogos Electorales rebota al dashboard"

`/master/configuracion` no estaba registrada (solo `/master/cargos`). Ahora existe
(`roles: ['MASTER']`) y `/master/cargos`, `/master/partidos` y `/master/candidatos` redirigen a
ella.

**El fix propuesto por el task habría duplicado la ruta.** Además, la versión propuesta usa
`children` con `redirectTo: 'cargos'` y componentes `MasterCargosPage` / `MasterPartidosPage` /
`MasterCandidatosPage` como rutas hijas, cuando el diseño real es **una sola página con tabs**
(`MasterConfiguracionPage` lee `data.tab` y `cambiarTab()`). El snippet del task rompería la
navegación por tabs.

---

## C — Qué se hizo: tests de regresión

No hubo fix de código que hacer: el código estaba bien. Lo que faltaba era **que nada volviera a
romperlo en silencio**. Eso es lo que se agrega.

### C.1 — `app.routes.spec.ts`: ninguna URL fantasma

El bug de origen era una **URL fantasma**: el menú apuntaba a una ruta que el router no tenía, y
la ruta comodin `**` la mandaba al dashboard. El síntoma era "click y aparece el dashboard, sin
error".

El test no mira dos rutas sueltas: mira **todas las rutas de los cuatro roles** y exige que cada
una exista en el router.

```ts
it('toda ruta de NAV_CONFIG existe en el router', () => { ... })
```

Es el test que habría atrapado los tres issues de origen. Además fija que
`/observer/mis-actas` admite OBSERVER y MASTER, que `/master/configuracion` admite MASTER, y que
las tres rutas `/master/*` redirigen a `/master/configuracion`.

### C.2 — `dashboard.api.spec.ts`: endpoints canónicos y cache sin errores

- Los endpoints `/resultados-detallados` y `/progreso-detallado` **no se piden nunca**: se
  verifica que no quede ninguna petición a ellos.
- **Un fallo no queda cacheado**: tras un 500, el reintento tiene que volver a pedir el endpoint
  (si el error quedara cacheado, el botón "reintentar" de la página no recuperaría sin recargar
  el navegador).

### C.3 — `progreso-cantones.rendering.spec.ts` (suite nueva)

El spec de la página probaba la **lógica** (`parroquiasOrdenadas`, orden, colores) pero **nunca
renderizaba la plantilla**. El contrato vista↔modelo quedaba sin cubrir: si la plantilla leyera
un campo que el modelo no declara, la lógica seguiría verde y las celdas saldrían vacías en
pantalla.

El spec nuevo renderiza el DOM y mira lo que el usuario ve: una fila por parroquia, el nombre
desde `parroquia.parroquiaNombre`, los conteos de JRVs y la barra de avance con su
`aria-valuenow`/`aria-label`.

---

## D — Mutaciones ejecutadas

Protocolo: `cp` a `.bak` antes de mutar, `cp` de vuelta para restaurar, `diff` contra el `.bak`
al final. **Nunca** `git checkout --` sobre trabajo sin commitear.

| # | Mutación | Test(s) que cayeron |
|---|---|---|
| M1 | Borrar la ruta `/observer/mis-actas` | `toda ruta de NAV_CONFIG existe en el router`<br>`/observer/mis-actas existe y admite OBSERVER y MASTER` — **2 de 9** |
| M2 | Borrar la ruta `/master/configuracion` | `toda ruta de NAV_CONFIG existe en el router`<br>`/master/configuracion existe y admite MASTER` — **2 de 9** |
| M3 | `/master/cargos` redirige a otro destino | `/master/cargos, /master/partidos y /master/candidatos redirigen a /master/configuracion` — **1 de 9** |
| M4 | Volver a pedir `/dashboard/resultados-detallados` | los 4 del bloque `DashboardApi — endpoints canonicos y cache sin errores` — **4 de 11** |
| M5' | `shareReplay(1)` → `publishReplay(1)` (este último **sí** cachea el error) | los 4 del bloque `DashboardApi — endpoints canonicos y cache sin errores` — **4 de 11** |
| M6 | La plantilla lee `parroquia.parroquiasNombre` (campo inexistente) | `should render the parroquia name from the model field` — **1 de 5** |

**6 mutaciones, 6 caen.** La primera tanda (M1–M4) se corrió contra un árbol de trabajo que un
proceso externo revirtió a mitad de camino: los tests "pasaron" porque el spec había vuelto a su
versión original. Se repitieron contra el baseline commiteado y ahí cayeron.

### D.1 — Una mutación inefectiva que NO se escondió

La primera versión del fix (commit `c47d266`) agregaba un `catchError` al cache de detalles del
dashboard "para que un fallo no quedara cacheado". **Era un no-op**, y la mutación que lo
delató fue: *quitar ese `catchError`*. **No cayó ningún test** → el test era decorativo.

La causa: **RxJS 7.8.2 ya lo resuelve.** `shareReplay` implementa
`share({ resetOnError: true })`: cuando la fuente falla, el `ReplaySubject` se resetea y el
siguiente suscriptor vuelve a suscribir la fuente, o sea que **dispara una petición HTTP nueva**.
El botón "reintentar" ya funcionaba.

```
frontend/app/node_modules/rxjs/dist/esm/internal/operators/shareReplay.js
  return share({
    connector: () => new ReplaySubject(bufferSize, windowTime, scheduler),
    resetOnError: true,        <-- acá está la garantía
    resetOnComplete: false,
    resetOnRefCountZero: refCount,
  });
```

**Qué se hizo con eso:** se revirtió el `catchError` (commit `9232df6`) y se dejó el test como
**contrato**, con el comentario que explica de dónde viene la garantía (`shareReplay`, no el
repo) para que nadie "re-arregle" un bug que no existe. La mutación que sí lo prueba es M5'
(`publishReplay`), que **cae**.

---

## E — Hallazgos (fuera de scope, anotados, no tocados)

### E.1 — El backend nunca manda el desglose por parroquia → la tabla siempre sale vacía

`ProgresoCantonDto` (backend) tiene 5 campos y **ninguno es `parroquias`**. El frontend sí lo
declara (`ParroquiaProgresoDto`) y la vista renderiza una tabla *"Desglose de Parroquias — {cantón}"*
con encabezados y **cero filas**, siempre.

El casteo es `parroquias: (c as ProgresoCantonDto).parroquias ?? []` en `getProgresoDetalles()`:
la clave no existe en la respuesta, así que siempre da `[]`.

**No se implementó.** Es una función nueva, no un fix: requiere una query nueva en
`DashboardService` (JRVs totals y con acta por parroquia, agrupados por cantón), agregar el campo
al DTO, actualizar el contrato y los tests. `AGENTS.md` pide plantear los cambios de producto
antes de ampliar el release, y el cambio de contrato de `/api/v1/dashboard/progreso` no se puede
verificar sin base de datos (no hay daemon de Docker en esta máquina).

**Es lo que sigue después de este ODD**, con acceso a Postgres.

### E.2 — Asimetría de contrato en `parroquias`

El modelo del frontend declara `parroquias` y el backend no lo manda. El `as` del cast
(`(c as ProgresoCantonDto).parroquias`) oculta esa diferencia al compilador: TypeScript no puede
avisar que el campo nunca llega. Si el backend lo llegara a mandar, el tipo del cast no lo
describiría igual. Es la clase de asimetría que un contrato generado (o un test de contrato que
compare el JSON real con el modelo) detectaría.

### E.3 — `PLACEHOLDER_ROUTES` lista rutas que ya funcionan

`nav.config.ts` tiene `/master/cargos`, `/master/partidos` y `/master/candidatos` en
`PLACEHOLDER_ROUTES`. Desde #175 esas tres **redirigen** a `/master/configuracion`, que sí
funciona: ya no son placeholders. Hoy no molesta (el `NAV_CONFIG` no las usa), pero la lista es
una trampa: si alguien agrega un item de menú a una de esas rutas,
`sanitizeNavSections` lo **borra en silencio** creyendo que es un placeholder.

### E.4 — El wildcard `**` sigue enmascarando los typos de ruta

`{ path: '**', redirectTo: '' }` manda cualquier ruta desconocida al dashboard. Es el mecanismo
que produjo los tres síntomas de este task, y lo convierte en "nada que pasó". Con el test de
regresión C.1 el hueco queda cubierto para el menú, pero un typo en una URL escrita a mano sigue
rebotando sin error visible.

---

## F — Cómo se reproduce

```bash
cd frontend/app

# Los tres specs nuevos
npx vitest run src/app/app.routes.spec.ts \
                src/app/features/dashboard/data/dashboard.api.spec.ts \
                src/app/features/dashboard/pages/progreso-cantones/progreso-cantones.rendering.spec.ts

# Gates completos
pnpm test:ci && pnpm lint && pnpm format:check && pnpm build
```

### Comprobación manual de los tres issues

```bash
# 1. Las rutas del menú existen (con sesion de MASTER)
#    /dashboard/resultados  /dashboard/progreso  /observer/mis-actas  /master/configuracion
# 2. Los endpoints que pide el frontend son los que expone el backend
curl -s http://localhost:8080/api/v1/dashboard/resultados -H "Authorization: Bearer <admin>" | head -c 200
curl -s http://localhost:8080/api/v1/dashboard/progreso  -H "Authorization: Bearer <admin>" | head -c 200
```

---

## G — Qué se verificó y qué no

**Verificado en esta sesión:**

- Los tres issues del task: **ya estaban arreglados** por `bdfa452` (#175). Verificado por
  lectura del código, del historial de git y del contrato backend↔frontend.
- Que los fixes propuestos por el task **revertirían** el código (apiUrl absoluta rompe el
  cookie httpOnly y el proxy; las rutas nuevas duplican las existentes; los `children` rompen la
  navegación por tabs).
- 6 mutaciones, 6 caen (tabla en D).
- Gates: `pnpm test:ci` (69 suites / 664 tests / 0 fallos), `pnpm lint`, `pnpm format:check`,
  `pnpm build`. Todo en verde.

**No verificado (declarado, no omitido):**

- **El comportamiento en un navegador real.** Se verificó el contrato HTTP y el DOM renderizado
  en jsdom, no la navegación real de un MASTER.
- **El backend con datos.** No hay daemon de Docker en esta máquina, así que no se pudo levantar
  Postgres ni correr `DashboardContractIT` (que sí cubre `/resumen` y `/resultados` contra un
  backend vivo). El contrato se verificó por lectura del código y del DTO.
- **El endpoint de progreso con el desglose por parroquia**, porque no existe (E.1).

---

**Estado de la implementacion tras este ODD:** los tres issues del task original están resueltos
y **bloqueados con tests de regresión** que caen si alguien deshace el fix de #175. Se agrega una
suite de rendering que cubría un hueco real del contrato vista↔modelo. Se **revierte** un fix
propio que resultó ser un no-op, en vez de dejarlo comme "mejora". Queda anotado el trabajo que
falta de verdad: el desglose por parroquia en el backend (E.1), que es función nueva y no fix.

Este archivo **no** declara el change listo para produccion: eso lo decide el verificador y el
Arquitecto.
