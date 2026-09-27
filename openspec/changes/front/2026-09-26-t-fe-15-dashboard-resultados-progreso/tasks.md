# Tasks — T-FE-15: Analítica Electoral — Resultados por Cargo y Progreso Cantonal (ADMIN/MASTER)

## Fase A: Modelos, Servicio de Dashboard y Rutas

- [ ] **A.1** — En `frontend/app/src/app/features/dashboard/models/dashboard.model.ts`:
  - Declarar interfaces `ResultadoCargoDto`, `CandidatoResultadoDto`, `ProgresoCantonDto`, `ParroquiaProgresoDto`.
- [ ] **A.2** — En `frontend/app/src/app/features/dashboard/services/dashboard.service.ts`:
  - Implementar llamadas HTTP tipadas:
    - `getResultados(): Observable<ResultadoCargoDto[]>` contra `/api/v1/dashboard/resultados`.
    - `getProgreso(): Observable<ProgresoCantonDto[]>` contra `/api/v1/dashboard/progreso`.
- [ ] **A.3** — En `frontend/app/src/app/app.routes.ts`:
  - Registrar rutas `/dashboard/resultados` y `/dashboard/progreso` (con alias `/admin/resultados` y `/admin/progreso`) protegidas con `roleGuard` para `['ADMIN', 'MASTER', 'COORDINATOR']`.
  - Actualizar enlaces correspondientes en `SidebarComponent`.

## Fase B: Componente Resultados por Cargo

- [ ] **B.1** — Crear `ResultadosCargoPage` en `frontend/app/src/app/features/dashboard/pages/resultados-cargo/`:
  - Selector de pestañas horizontales por dignidad/cargo.
  - Tarjeta lateral/superior con candidato/a puntero/a (foto, votos, porcentaje, estado provisional).
  - Lista de candidatos con barras horizontales proporcionales (`style.width.%`).
  - Tarjetas resumen con desglose de Votos Válidos, Votos en Blanco y Votos Nulos.
  - Estado de carga con `SkeletonComponent` y manejo de error con `ErrorStateComponent`.

## Fase C: Componente Progreso por Cantón

- [ ] **C.1** — Crear `ProgresoCantonesPage` en `frontend/app/src/app/features/dashboard/pages/progreso-cantones/`:
  - Tarjetas cantonales (Santa Elena, La Libertad, Salinas) con barra de progreso estilizada (`fill-green`, `fill-blue`, `fill-amber`).
  - Métricas de JRVs escrutadas vs JRVs totales por cantón.
  - Tabla de desglose territorial con avance por parroquias.

## Fase D: Tests Unitarios y Verificación de Mutaciones

- [ ] **D.1** — En `resultados-cargo.page.spec.ts`:
  - Test de renderizado del cargo activo y cambio de pestaña.
  - Test de cálculo visual del porcentaje de la barra.
  - Test de visualización de blancos y nulos.
- [ ] **D.2** — En `progreso-cantones.page.spec.ts`:
  - Test de renderizado de las tarjetas cantonales.
  - Test de asignación de clase de color según porcentaje de avance.
- [ ] **D.3** — Verificación por mutación, ejecutada por quien implementa:
  - Cambiar el cálculo del ancho de la barra para devolver siempre `100%` y comprobar que **cae** el test de renderizado proporcional. Anotá en `apply-progress.md` **el nombre del test que cayó**.
  - Forzar que el selector de pestañas ignore el índice seleccionado y comprobar que **cae** el test de cambio de cargo. Anotá en `apply-progress.md` **el nombre del test que cayó**.

---

## Qué NO hacer en esta fase

- NO recalcular subtotales en el cliente ignorando los datos de PostgreSQL.
- NO incorporar formularios de edición o mutación en estas vistas (son 100% de solo lectura).
- NO duplicar llamadas HTTP al cambiar de pestaña; los resultados de todos los cargos vienen en un único payload.

---

## Compuertas antes de dar por terminada la fase

```bash
cd frontend/app
pnpm lint
pnpm test:ci
```
- `0 errores de lint`
- Todos los tests unitarios nuevos en verde y suites previas pasando intactas.
