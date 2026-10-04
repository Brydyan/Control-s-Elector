# Tasks — T-FE-11: Dashboard Reactivo en Tiempo Real e Integración REST

## Fase A: DashboardService y Modelos DTO

- [x] **A.1** — Crear modelos en `features/dashboard/models/dashboard.model.ts`:
  - `ResumenActas`, `ResultadoCargo`, `ProgresoCanton`, `Novedad`.
- [x] **A.2** — Implementar `features/dashboard/services/dashboard.service.ts`:
  - `getResumen(): Observable<ResumenActas>` -> `GET /api/v1/dashboard/resumen`
  - `getResultados(): Observable<ResultadoCargo[]>` -> `GET /api/v1/dashboard/resultados`
  - `getProgreso(): Observable<ProgresoCanton[]>` -> `GET /api/v1/dashboard/progreso`
  - `getNovedades(): Observable<Novedad[]>` -> `GET /api/v1/dashboard/novedades`
  - `getDashboardCompleto(): Observable<DashboardData>` con `forkJoin`.
- [x] **A.3** — Tests unitarios en `dashboard.service.spec.ts`.

## Fase B: Conexión de DashboardPage con Backend

- [x] **B.1** — Reemplazar datos mockeados en `features/dashboard/dashboard.page.ts` por llamadas reactivas a `DashboardService`.
- [x] **B.2** — Renderizado dinámico de tarjetas de estadísticas y barras de progreso por cantón.
- [x] **B.3** — Tests unitarios en `dashboard.page.spec.ts`.

## Fase C: Supabase Realtime e Invalidación con Debounce

- [x] **C.1** — Implementar canal WebSocket en `DashboardService` o servicio dedicado:
  - Suscripción a eventos `postgres_changes` en tabla `acta`.
  - Tubería con `Subject` y operador `debounceTime(500)` para amortiguar ráfagas de actas concurrentes.
  - Disparo de recarga de `getDashboardCompleto()`.
- [x] **C.2** — Limpieza en `ngOnDestroy` (`channel.unsubscribe()`).
  > El wiring con cliente Supabase real queda en `app.config.ts` (fuera de scope — pendiente de variables de entorno `SUPABASE_URL` / `SUPABASE_ANON_KEY`). El servicio acepta cliente vía `SUPABASE_REALTIME_CLIENT` token; tests usan stub.

## Verificación por Mutación

- **V.1** — Comentar `debounceTime` -> simular 10 eventos simultáneos provoca 10 peticiones HTTP paralelas (detectar fallo).
- **V.2** — Cambiar ruta de `/api/v1/dashboard/resumen` a `/api/resultados/consolidado` -> petición debe arrojar 404 (detectar fallo).
