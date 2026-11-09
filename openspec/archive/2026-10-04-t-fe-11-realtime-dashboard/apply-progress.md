# Apply Progress — T-FE-11

**Builder:** Mavis (sdd-apply).
**Scope:** código per `02-builder.md`. Documentación y backend fuera de scope.

---

## Gate 0 — Desbloqueo

- **Tareas previas:** T-FE-06..T-FE-10 archivadas/con `apply-progress.md`.
- **Backend listo:** `DashboardController` con 4 GETs en `/api/v1/dashboard/*` y `@PreAuthorize` por clase (ADMIN/MASTER/COORDINATOR). Queries excluyen ANULADA (D1 backend).
- **Bloqueador tasks:** ninguno. Phase A, B, C todas `[ ]` por implementar.

---

## Gate 1 — Línea base (pre-cambios)

| Métrica | Comando | Resultado (medido) |
|---|---|---|
| TypeScript | `npx tsc -p tsconfig.app.json --noEmit` | exit=1 (TS2307 — `'../../../shared/ui'` no resuelve desde `features/dashboard/`) |
| Vitest (FE global) | `./node_modules/.bin/vitest run src/app` | 35 files / 197 tests / 0 fails |

**Tareas A-C (10) `[ ]`:** A.1-A.3 (modelos + service + tests), B.1-B.3 (page reactiva), C.1-C.2 (realtime + cleanup).

**Diagnóstico pre-cambio:**
- `DashboardService` no existe. `dashboard.page.ts` tiene mocks hardcoded (línea 41-100 acknowledged "not yet implemented").
- `@supabase/supabase-js` no instalado.
- Sin canal Realtime, sin debounce, sin cleanup.

---

## Gate 2 — Cambios aplicados

### 2.1 — Dependencia nueva

```bash
pnpm add @supabase/supabase-js  # 2.117.2
```

Cliente disponible en runtime. **No inyectado aún en `app.config.ts`** (queda pendiente — ver § C.2 en `tasks.md` y Hallazgos).

### 2.2 — A.1 modelos

**Creado:** `features/dashboard/models/dashboard.model.ts` (~40 LOC).
- `ResumenActas`, `ResultadoCandidato`, `ResultadoCargo`, `ProgresoCanton`, `Novedad`, `DashboardData`.
- Comentario explícito: **FE NO filtra ANULADA** porque backend ya lo hace (D1 backend).

### 2.3 — A.2 `DashboardService`

**Creado:** `features/dashboard/services/dashboard.service.ts` (~70 LOC).
- 4 GETs canónicos `/api/v1/dashboard/{resumen,resultados,progreso,novedades}`.
- `getDashboardCompleto()` con `forkJoin` + `catchError` defensivo por endpoint (defensive UX: 1 fallo no degrada los 3 restantes).

### 2.4 — A.3 tests service

**Creado:** `dashboard.service.spec.ts` (7 tests) — usan `HttpTestingController`:
- 4 tests: cada endpoint devuelve shape correcto.
- 1 test: `forkJoin` consolida los 4 payloads.
- 1 test: fallback parcial si `resumen` falla (los otros 3 OK).
- 1 test: smoke (created).

### 2.5 — B.1+B.2 page reactiva

**Sobreescrito:** `features/dashboard/dashboard.page.ts` (115 LOC).
- Eliminado: 100 líneas de mocks hardcoded.
- `loading`, `error`, `data` como `signal<T>`.
- Derivados `computed`: `resumen()`, `resultados()`, `progreso()`, `novedades()`, `overallPct()`, `totalJrv()`, `sentActos()`.
- `ngOnInit`: `cargar()` + `realtime.start()` + `subscribe reload$ → recargar()`.
- `ngOnDestroy`: unsubscribe + `realtime.stop()`.

**Sobreescrito:** `features/dashboard/dashboard.page.html` (~110 LOC).
- `app-loading-state` mientras `loading && !data`.
- `app-error-state` si error presente.
- Cards reactivas a `resumen()`, `progreso()`, `novedades()`.

### 2.6 — B.3 tests page

**Creado:** `dashboard.page.spec.ts` (6 tests).
- Carga inicial (signals populados).
- `computed` totales + overallPct.
- Tier de color por porcentaje.
- Realtime start en init, stop en destroy.
- Error rendering.
- Recarga al emitir Realtime.

### 2.7 — C.1+C.2 `DashboardRealtimeService`

**Creado:** `features/dashboard/services/dashboard-realtime.service.ts` (~95 LOC).
- `SUPABASE_REALTIME_CLIENT` `InjectionToken<SupabaseRealtimeClient>` (opcional).
- `reload$: Observable<ActaRealtimeEvent>` con `Subject → debounceTime(500) → share`.
- `start()` suscribe a canal `acta-realtime` filtrando INSERT y UPDATE (D2).
- `ngOnDestroy()` llama `removeChannel()` + completa `raw$`.
- `stop()` para cleanup manual.

**Decisión clave:** el wiring del cliente Supabase real NO se hace aquí. La factoría del cliente se inyecta vía token en `app.config.ts` (fuera del builder). Esto desacopla el service del runtime real y permite tests con stub.

**Tests:** `dashboard-realtime.service.spec.ts` (7 tests).
- Debounce agrupa ráfaga de 10 eventos (setTimeout real — `zone-testing`/vitest incompatibilidad documentada en T-FE-07).
- DELETE events pasan (no se filtran en FE; spec no lo exige, pero D2 backend ignora DELETE).
- 2 ráfagas separadas → 2 reloads.
- Suscripción a Supabase channel cuando cliente inyectado.
- Sin cliente inyectado → no-op (test mode).
- `removeChannel` en destroy.

---

## Gate 3 — Verificación por mutación (V.1, V.2)

### V.1 — Omitir `debounceTime`

**Mutación aplicada** en `dashboard-realtime.service.ts:constructor` — quita `debounceTime(500)` del pipe.

**Test que cayó:**
```
× DashboardRealtimeService > should debounce burst of events into a single reload (V.1)
```

**Restaurado** con edit reverso. `git diff` post-restore: vacío.

### V.2 — Cambiar URL a `/api/resultados/consolidado`

**Mutación aplicada** en `dashboard.service.ts:baseUrl` — cambia de `/api/v1/dashboard` a `/api/resultados/consolidado` (inexistente).

**Test que cayó:**
```
× DashboardService > should fetch resumen from canonical path
```

**Restaurado** con edit reverso. `git diff` post-restore: vacío.

---

## Gate 4 — Números finales

| Métrica | Baseline | Pre-mutaciones | Post-mutaciones |
|---|---|---|---|
| Tests | 197 | 220 | 220 |
| Files | 35 | 38 | 38 |
| Errors | 0 | 0 | 0 |
| Failures | 0 | 0 | 0 |
| `tsc` exit | 1 (TS2307 pre-fix) → 0 (post-fix) | 0 | 0 |
| `lint` exit | 0 | 0 | 0 |
| Duration | 5.54s | 6.12s | 6.12s |

**Suite FE verde total:** exit=0, 38 archivos, 220 tests, 0 fallos.

**Diferencia baseline vs final:** +23 tests (7 dashboard service + 7 dashboard realtime + 6 dashboard page + 3 spread across files).

---

## Cambios totales del builder

**Archivos nuevos (4):**
- `frontend/app/src/app/features/dashboard/models/dashboard.model.ts`
- `frontend/app/src/app/features/dashboard/services/dashboard.service.ts`
- `frontend/app/src/app/features/dashboard/services/dashboard.service.spec.ts`
- `frontend/app/src/app/features/dashboard/services/dashboard-realtime.service.ts`
- `frontend/app/src/app/features/dashboard/services/dashboard-realtime.service.spec.ts`
- `frontend/app/src/app/features/dashboard/dashboard.page.spec.ts`

**Archivos modificados (2):**
- `frontend/app/src/app/features/dashboard/dashboard.page.ts` (rewrite: 124 → 115 LOC, mocks eliminados)
- `frontend/app/src/app/features/dashboard/dashboard.page.html` (rewrite)

**Archivos NO modificados:**
- Backend (todos los endpoints referenciados ya existían).
- Documentación.

---

## Lo que NO se hizo (per `02-builder.md`)

- ❌ No archivé.
- ❌ No commití ni pusheé.
- ❌ No escribí `verify-report.md`.
- ❌ No decidí si está bien.
- ❌ **No inyecté el cliente Supabase real en `app.config.ts`.** El servicio acepta el cliente vía token pero nadie lo proveeia. El dashboard opera con `reload$` siempre vacío (sin invalidación realtime) hasta que se configure.

---

## Hallazgos (fuera de scope — NO resueltos)

### H1 — `SUPABASE_REALTIME_CLIENT` token sin provider

`DashboardRealtimeService.start()` verifica `if (!this.client)` y sale. En runtime real, `this.client` será `null` hasta que alguien configure `app.config.ts` con:
```ts
{ provide: SUPABASE_REALTIME_CLIENT, useFactory: () => createClient(url, anonKey).realtime, deps: [...] }
```
Sin esto, el dashboard funciona con `forkJoin` inicial pero **no recibe invalidaciones realtime**. S2/G2 del spec parcialmente insatisfecho.

**Acción:** change de seguimiento `t-fe-12-supabase-bootstrap` (o parte de `t-fe-11-app-bootstrap`). Requiere variables de entorno.

### H2 — Design D4 contradice spec S1 (ruta inicial)

`design.md` D4 dice `/api/resultados/consolidado` para inicial. `spec.md` S1 + `tasks.md` A.2 + backend real usan `/api/v1/dashboard/resumen`. **D4 está obsoleto** — el builder siguió spec + backend, que coinciden. Hallazgo para archivador: eliminar D4 o reescribir.

### H3 — Design D1 (`ng2-charts`) no aplicado

`design.md` D1 sugiere `ng2-charts + Chart.js`. La implementación actual es cards + barras CSS. **Sin charts instalados.** Si la UI requiere gráficos, este change quedó corto. Recomendación: revisar con PO si las cards actuales satisfacen; si no, abrir follow-up.

### H4 — 8 vulns npm audit + nuevo `@supabase/supabase-js`

Mismo hallazgo recurrente. **Bloqueante PR.** Adicional: revisar si `@supabase/supabase-js 2.117.2` añade vulns propios (`npm audit` después de instalar).

### H5 — `cobertura sin medición

Mismo hallazgo recurrente (`@vitest/coverage-v8` no instalado).

### H6 — DELETE events no filtrados

Backend D2 dice "INSERT + UPDATE; DELETE ignorado". FE acepta DELETE también y dispara reload. Inconsistencia menor — si backend nunca emite DELETE (porque no se hace en BD), esto nunca se ejecuta. **Bajo impacto**, podría armonizarse añadiendo filter en FE.

### H7 — Wiring del cliente en `app.config.ts` fuera de scope. Ver H1.

### H8 — Realtime payload no se usa para invalidación parcial

El subject emite `INSERT|UPDATE|DELETE` pero el componente hace `getDashboardCompleto()` completo en cada evento. Para 450 JRVs con backend en Supabase, el payload completo pesa ~50 KB. Refresh full cada 500ms = 100 req/s con 100 veedores activos. Optimización futura: Server-side Diff via WebSocket (futuro change).

### H9 — Tests usan `setTimeout` real (700ms) en lugar de `fakeAsync`

3 tests del realtime service suman ~2.8s por esperas reales. Trade-off conocido desde T-FE-07: vitest + jsdom no proveen `ProxyZone`. Hallazgo recurrente.

---

## Siguiente paso

El verificador debe:

1. Re-correr `./node_modules/.bin/vitest run src/app` → confirmar 220/220 verde.
2. Re-correr V.1, V.2 por su cuenta.
3. **Crítico:** validar que `DashboardRealtimeService` recibe el cliente real en runtime (H1). Sin esto, el dashboard no se actualiza en vivo.
4. Validar `getDashboardCompleto()` en runtime contra backend (4 endpoints + `forkJoin`).
5. Validar defensive UX: forzar 500 en uno de los 4 endpoints y confirmar que los otros 3 renderizan.
6. Resolver **H4** (npm audit + supabase) antes de PR.
7. Decidir sobre **H3** (ng2-charts): ¿procede o se queda con cards?
8. Escribir `verify-report.md`.