# Tasks — T-FE-06: Observer: Flujo de JRVs Asignadas, Dignidades y Consulta de Actas

## Fase A: ObserverService y Modelos DTO

- [x] **A.1** — Crear modelos en `features/observer/models/observer.model.ts`:
  - `JrvConEstado`, `CargoConEstado`, `CandidatoInfo`, `ActaDetalleItem`, `ActaRequest`, `ActaResponse`, `EstadoActa`.
- [x] **A.2** — Implementar métodos en `features/observer/services/observer.service.ts`:
  - `getMisJrvs(): Observable<JrvConEstado[]>` -> `GET /api/v1/observer/jrvs`
  - `getCargosDeJrv(jrvId: number): Observable<CargoConEstado[]>` -> `GET /api/v1/observer/jrv/{jrvId}/cargos`
  - `getMisActas(): Observable<ActaResponse[]>` -> `GET /api/v1/observer/mis-actas`
  - `getCorreccionesPendientes(): Observable<ActaResponse[]>` -> `GET /api/v1/observer/correcciones-pendientes`
- [x] **A.3** — Tests unitarios de `ObserverService` en `observer.service.spec.ts`.

## Fase B: MisJrvsPage

- [x] **B.1** — Implementar `features/observer/pages/mis-jrvs/mis-jrvs.page.ts`:
  - Consumo reactivo de `getMisJrvs()`.
  - Pestañas de filtrado: `TODAS`, `PENDIENTES`, `COMPLETADAS`.
  - Contadores `totalJrvs`, `totalPendientes`, `totalCompletadas`.
  - Navegación al seleccionar JRV: `/observer/jrv/:jrvId/cargos`.
- [x] **B.2** — Template accesible y responsivo con componentes de UI (`BadgeComponent`, `CardComponent`, etc.).
- [x] **B.3** — Tests unitarios en `mis-jrvs.page.spec.ts`.

## Fase C: JrvCargosPage

- [x] **C.1** — Implementar `features/observer/pages/jrv-cargos/jrv-cargos.page.ts`:
  - Lectura de parámetro de ruta `jrvId`.
  - Consumo de `getCargosDeJrv(jrvId)`.
  - Mapeo visual de cada dignidad con su badge de estado.
  - Navegación al formulario de registro: `/observer/jrv/:jrvId/cargo/:cargoId/registrar`.
  - Botón de regreso a `/observer/mis-jrvs`.
- [x] **C.2** — Tests unitarios en `jrv-cargos.page.spec.ts`.

## Fase D: Rutas y Verificación

- [x] **D.1** — Registrar rutas en `app.routes.ts` con `roleGuard(['OBSERVER', 'MASTER'])`.
- [x] **D.2** — Ejecutar suite completa con `npm test -- --run` asegurando 100% verde.

## Verificación por Mutación

- **V.1** — Invertir condición del filtro `PENDIENTES` (`j.cargosPendientes === 0`) -> `mis-jrvs.page.spec.ts` debe fallar.
- **V.2** — Cambiar ruta de navegación en `seleccionarJrv` -> test de navegación debe fallar.
