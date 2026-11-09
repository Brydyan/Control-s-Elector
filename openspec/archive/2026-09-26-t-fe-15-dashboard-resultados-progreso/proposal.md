# Proposal — T-FE-15: Analítica Electoral — Resultados por Cargo y Progreso Cantonal (ADMIN/MASTER)

## Qué se construye
Módulo de visualización y análisis de escrutinio para directivos (`ADMIN`, `MASTER`, `COORDINATOR`), implementando dos pantallas especializadas según los mockups [`ADMIN/03-resultados-por-cargo.html`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/documents/mockups/ADMIN/03-resultados-por-cargo.html) y [`ADMIN/04-progreso-cantones.html`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/documents/mockups/ADMIN/04-progreso-cantones.html):
1. **Resultados Electorales por Dignidad (`/admin/resultados` o `/dashboard/resultados`)**:
   - Pestañas/selector de dignidades: Prefecto/a, Alcaldes (por cantón), Concejales y Consejeros CPCCS.
   - Tarjeta destacada de candidato/a puntero/a con porcentaje y votos absolutos.
   - Barras comparativas de resultados por lista/partido, incluyendo foto de candidato, logo partidario, número de lista, votos válidos y porcentaje.
   - Desglose consolidado de votos válidos, votos en blanco y votos nulos.
   - Consumo de `GET /api/v1/dashboard/resultados`.
2. **Progreso y Cobertura por Cantón (`/admin/progreso` o `/dashboard/progreso`)**:
   - Tarjetas métricas por cantón (Santa Elena, La Libertad, Salinas): % de avance, total de JRVs vs JRVs escrutadas, actas pendientes.
   - Barras de progreso con código semántico de color (Verde $\ge 70\%$, Azul $\ge 40\%$, Ámbar $< 40\%$).
   - Tabla de desglose territorial por parroquias con avance porcentual individual.
   - Consumo de `GET /api/v1/dashboard/progreso`.

## Por qué
- La vista principal de Dashboard (`DashboardPage`) solo presenta un resumen de tarjetas con datos hardcodeados pendientes de conexión.
- El CNE y la dirección política requieren monitorear en tiempo real tanto la correlación de votos por candidato como el ritmo de procesamiento de actas por cada territorio para alertar demoras en la transmisión.
- El backend ya implementó y certificó `GET /api/v1/dashboard/resultados` y `GET /api/v1/dashboard/progreso` en `DashboardController` con exclusión transaccional de actas anuladas.

## Alcance

| Elemento | Incluye | No incluye |
|---|---|---|
| **Vistas** | `ResultadosCargoPage` y `ProgresoCantonesPage` (o subtabs integradas) | Vistas operativas de digitación de actas |
| **Componentes** | Tabs de cargo, tarjeta de ganador provisorio, barras de progreso, tabla de votos válidos/blancos/nulos, tarjetas cantonales | Modelos matemáticos de predicción de boca de urna |
| **Servicios** | `DashboardService.getResultados()` y `DashboardService.getProgreso()` | Métodos de modificación de actas |
| **Permisos** | Acceso para `ADMIN`, `MASTER` y `COORDINATOR` en modo lectura | Edición o manipulación de datos electorales |

## Criterios de Aceptación

- [ ] La navegación desde la barra lateral (`Sidebar`) permite acceder a "Resultados por Cargo" y "Progreso por Cantón".
- [ ] En Resultados, cambiar de dignidad actualiza reactivamente el candidato líder, las barras de porcentaje y el subtotal de votos sin recargar la página.
- [ ] En Progreso, se muestran los 3 cantones de Santa Elena con sus porcentajes calculados por el backend a partir de las actas recibidas.
- [ ] Las vistas son de solo lectura estricta (no contienen botones de acción administrativa).
- [ ] Soporte responsive: se adapta a visualización desktop (mockup desktop) y móvil (mockup mobile).
