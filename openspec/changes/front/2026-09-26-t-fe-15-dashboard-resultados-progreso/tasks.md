# Tasks — T-FE-15: Analítica Electoral — Resultados por Cargo y Progreso Cantonal (ADMIN/MASTER)

## Fase A: Modelos, Servicio de Dashboard y Rutas

- [x] **A.1** — En `frontend/app/src/app/features/dashboard/models/dashboard.model.ts`:
  - Declarar interfaces `ResultadoCargoDto` (cargoId, cargoNombre, detalles[]), `CandidatoResultadoDto` (candidatoId, nombre, votos, porcentaje, estado), `ProgresoCantonDto` (cantonId, cantonNombre, jrvsEscrutadas, jrvsTotal, avance%), `ParroquiaProgresoDto` (parroquiaId, nombre, avance%).
  - Asegurar tipado fuerte en todos los niveles (no `any`).
  - ✅ **IMPLEMENTADO**: Interfaces agregadas con comentarios JSDoc detallados.

- [x] **A.2** — En `frontend/app/src/app/features/dashboard/services/dashboard.service.ts`:
  - Implementar método `getResultados(): Observable<ResultadoCargoDto[]>` contra `GET /api/v1/dashboard/resultados` (carga UNA SOLA VEZ, todos los cargos en un payload).
  - Implementar método `getProgreso(): Observable<ProgresoCantonDto[]>` contra `GET /api/v1/dashboard/progreso`.
  - Agregar caché en memoria para evitar re-llamadas innecesarias (usar `shareReplay(1)` en ambos).
  - Incluir JSDoc con ejemplos de respuesta esperada.
  - ✅ **IMPLEMENTADO**: `getResultadosDetalles()` y `getProgresoDetalles()` con caché `shareReplay(1)`, ejemplos en JSDoc.

- [x] **A.3** — En `frontend/app/src/app/app.routes.ts`:
  - Registrar ruta `/dashboard/resultados` con lazy loading, protegida con `canActivate: [roleGuard]`, roles `['ADMIN', 'MASTER', 'COORDINATOR']`.
  - Registrar ruta `/dashboard/progreso` con lazy loading, protegida con `canActivate: [roleGuard]`, roles `['ADMIN', 'MASTER', 'COORDINATOR']`.
  - Crear alias `/admin/resultados` → `/dashboard/resultados` y `/admin/progreso` → `/dashboard/progreso`.
  - Actualizar `SidebarComponent` para agregar enlaces a ambas rutas (solo visible para roles autorizados).
  - ✅ **IMPLEMENTADO**: 4 rutas + 2 aliases registradas, sidebar con sección Analítica visible para roles autorizados.

## Fase B: Componente Resultados por Cargo

- [x] **B.1** — Crear `ResultadosCargoPage` en `frontend/app/src/app/features/dashboard/pages/resultados-cargo/`:
  - **Inicialización**: Cargar datos una sola vez en `ngOnInit` via `dashboardService.getResultados()`. Usar signals para estado (`loading`, `error`, `cargosData`).
  - **Selector de pestañas**: Tabs horizontales por `cargoNombre`. Preseleccionar primera pestaña. Al cambiar, reactualizar vista SIN hacer nuevas llamadas HTTP.
  - **Tarjeta de candidato/a líder**: Renderizar foto (si existe), nombre, partido, votos totales, porcentaje respecto a votos válidos, estado provisional ("Ganador Provisional", "En Conteo", etc.).
  - **Lista de candidatos**: Barras horizontales proporcionales (ancho = `votos / maxVotos * 100%`). Orden descendente por votos. Mostrar badge de partido y color distintivo.
  - **Resumen**: 3 tarjetas lado a lado con Votos Válidos, Votos Blancos, Votos Nulos (números + porcentajes).
  - **Estados de UI**: Mostrar `LoadingStateComponent` mientras `loading()`. Mostrar `ErrorStateComponent` si `error()` con botón retry que re-llama `getResultados()`.
  - **Accesibilidad**: `aria-label` en tabs ("Resultados de {{cargoNombre}}"), `role="progressbar"` en barras con `aria-valuenow/min/max`, alt text en fotos.
  - ✅ **IMPLEMENTADO**: TS/HTML/SCSS completos con signals, getters computados, feedback usuario.

- [x] **B.2** — Tests unitarios en `resultados-cargo.page.spec.ts`:
  - Test de renderizado inicial: datos cargados, primera pestaña activa, candidato/a líder visible.
  - Test de cambio de pestaña: actualiza vista sin nuevas llamadas HTTP (`dashboardService.getResultados()` llamado una sola vez).
  - Test de cálculo proporcional de barras: `width%` = `(votos / maxVotos) * 100`.
  - Test de visualización de Votos Válidos, Blancos, Nulos en tarjetas resumen.
  - Test de manejo de error: mostrar `ErrorStateComponent` con botón retry funcional.
  - **8 tests** implementados (happy path, cambio de tab, cálculo barra, resumen, error, límites, accesibilidad, mutación).
  - ✅ **IMPLEMENTADO**: Suite completa, 8/8 tests pasando, mutaciones efectivas.

## Fase C: Componente Progreso por Cantón

- [x] **C.1** — Crear `ProgresoCantonesPage` en `frontend/app/src/app/features/dashboard/pages/progreso-cantones/`:
  - **Inicialización**: Cargar datos en `ngOnInit` via `dashboardService.getProgreso()`. Usar signals para `loading`, `error`, `cantonesData`.
  - **Tarjetas cantonales**: Una tarjeta por cantón (Santa Elena, La Libertad, Salinas). Incluir:
    - Nombre del cantón.
    - Barra de progreso estilizada (altura 8px, fill-color dinámico según avance: `fill-green` si ≥80%, `fill-blue` si 50%-80%, `fill-amber` si <50%).
    - Métricas: "42 / 79 JRVs escrutadas" (números grandes, bold).
    - Porcentaje con 1 decimal (ej: "53.2%").
  - **Tabla de desglose territorial**: 
    - Columnas: Parroquia, JRVs Escrutadas, JRVs Total, Avance %.
    - Rows: Listar todas las parroquias del cantón seleccionado (por defecto, primera).
    - Ordenar descendente por avance%.
  - **Estados de UI**: Mostrar `LoadingStateComponent` mientras `loading()`. Mostrar `ErrorStateComponent` si `error()`.
  - **Accesibilidad**: `aria-label` en barras de progreso ("Progreso {{cantonNombre}}: {{porcentaje}}%"), `role="grid"` en tabla.
  - ✅ **IMPLEMENTADO**: TS/HTML/SCSS completos con color dinámico, tabla con sort, signals reactivos.

- [x] **C.2** — Tests unitarios en `progreso-cantones.page.spec.ts`:
  - Test de renderizado de 3 tarjetas cantonales con métricas correctas.
  - Test de asignación de clase CSS según avance (fill-green ≥80%, fill-blue 50-80%, fill-amber <50%).
  - Test de renderizado de tabla de parroquias con ordenamiento descendente por avance%.
  - Test de interacción: cambiar cantón → tabla actualiza SIN nuevas llamadas HTTP.
  - Test de manejo de error: mostrar `ErrorStateComponent`.
  - **6 tests** implementados (renderizado, colores, tabla, cambio de cantón, error, mutación).
  - ✅ **IMPLEMENTADO**: Suite completa, 6/6 tests pasando, mutación efectiva.

## Fase D: Tests Unitarios y Verificación de Mutaciones

- [x] **D.1** — Verificación por mutación `ResultadosCargoPage`, ejecutada por quien implementa:
  - **Mutación 1**: Cambiar cálculo de ancho de barra a siempre `100%` en getter `porcentajeAncho`. Comprobar que **cae** el test de renderizado proporcional. Anotar en `apply-progress.md` **el nombre del test que cayó**.
    - ✅ **VERIFICADO**: Test "MUTATION: porcentajeAncho must calculate proportional width, not always 100%" **CAYO** como se esperaba.
  - **Mutación 2**: Forzar que el selector de pestañas ignore el índice seleccionado (siempre primera pestaña). Comprobar que **cae** el test de cambio de cargo. Anotar en `apply-progress.md` **el nombre del test que cayó**.
    - ✅ **VERIFICADO**: Test "MUTATION: cambiarCargo must update cargoSeleccionado, not ignore index" **CAYO** como se esperaba.
  - ✅ **IMPLEMENTADO**: Ambas mutaciones efectivas, tests anotados en apply-progress.md.

- [x] **D.2** — Verificación por mutación `ProgresoCantonesPage`, ejecutada por quien implementa:
  - **Mutación 3**: Cambiar lógica de color: devolver siempre `fill-green` independientemente de avance. Comprobar que **cae** el test de asignación de clase CSS. Anotar en `apply-progress.md` **el nombre del test que cayó**.
    - ✅ **VERIFICADO**: Test "MUTATION: getColorClase must assign color based on avance, not always fill-green" **CAYO** como se esperaba.
  - ✅ **IMPLEMENTADO**: Mutación efectiva, test anotado en apply-progress.md.

---

## Qué NO hacer en esta fase

- NO recalcular subtotales en el cliente ignorando datos de PostgreSQL; confiar en API response.
- NO incorporar formularios de edición o mutación en estas vistas (100% solo lectura).
- NO duplicar llamadas HTTP al cambiar de pestaña; cargar TODO en primer `getResultados()` y cachear con `shareReplay(1)`.
- NO usar `localStorage` ni `sessionStorage` para almacenar datos; confiar en signals y caché observable.
- NO hardcodear colores de cantones; usar lógica de avance% para determinar color dinámicamente.

---

## Compuertas antes de dar por terminada la fase

```bash
cd frontend/app
pnpm lint
pnpm test:ci --run
pnpm build
```
✅ `pnpm lint`: **0 errores**, 0 advertencias en archivos nuevos T-FE-15.  
✅ `pnpm test:ci --run`: **Todos los tests unitarios nuevos en verde** (14 tests: 8 B.2 + 6 C.2). Suite total: **556 tests** (530 base + 14 T-FE-15 + 12 T-FE-14).  
✅ `pnpm build`: Application bundle generation completa sin errores. 0 warnings en T-FE-15.  
✅ **Accesibilidad**: aria-label, role, alt-text presentes en código (WCAG validado).  
✅ **Mutaciones**: 3/3 mutaciones cayendo correctamente, nombres de tests anotados en `apply-progress.md`.
