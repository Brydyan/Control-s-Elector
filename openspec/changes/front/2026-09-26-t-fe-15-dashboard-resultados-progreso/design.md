# Design — T-FE-15: Analítica Electoral — Resultados por Cargo y Progreso Cantonal (ADMIN/MASTER)

---

## D1 — Arquitectura de Vistas: Páginas Dedicadas vs Acordeón en Dashboard

**Decisión**:
Crear dos rutas y páginas independientes en `app.routes.ts`:
- `/dashboard/resultados` (alias `/admin/resultados`)
- `/dashboard/progreso` (alias `/admin/progreso`)
Ambas páginas se integran en el `MainLayoutComponent` y quedan vinculadas directamente a los ítems del menú del `SidebarComponent`.

**Alternativa descartada — Incrustar todos los gráficos en un único dashboard con scroll vertical**:
Descartada porque en pantallas móviles y tablets, acumular en una sola pantalla el resumen general, más las 5 dignidades electorales, más las tablas parroquiales genera un documento excesivamente largo que degrada el rendimiento de renderizado y confunde al usuario en momentos de alto tráfico electoral.

---

## D2 — Autoridad de Cálculo: Backend Authority Estricto

**Decisión**:
El frontend no calcula porcentajes electorales a partir de actas individuales ni agrupa candidatos. Consume directamente los DTOs oficiales de PostgreSQL:
- `ResultadoCargoDto`: contiene nombre del cargo, candidato puntero, lista de candidatos con sus votos ya totalizados, votos blancos y votos nulos.
- `ProgresoCantonDto`: contiene nombre del cantón, JRVs totales, JRVs procesadas y porcentaje computado.
El cliente solo normaliza el cálculo de porcentajes relativos para renderizar el ancho en píxeles/porcentaje de las barras CSS (`style="width: ${candidato.porcentaje}%"`).

**Alternativa descartada — Descargar todas las actas y procesar sumatorias en Angular**:
Descartada porque viola la regla fundamental de arquitectura del proyecto: el backend es la única fuente de verdad contable y excluye de forma transaccional las actas anuladas o en observación. Calcular en cliente provocaría discrepancias visuales.

---

## D3 — Manejo de Estado y Caché con Signals / RxJS

**Decisión**:
Los servicios de analítica en `DashboardService` exponen señales (`signals`) o observables con `shareReplay({ bufferSize: 1, refCount: true })`.
Al cambiar de pestaña de dignidad dentro de la página de resultados, la selección se gestiona en memoria sin volver a golpear el backend, ya que `GET /api/v1/dashboard/resultados` devuelve todos los cargos en una sola respuesta eficiente (menos de 50 KB).

**Alternativa descartada — Hacer una petición HTTP por cada clic en un tab de cargo**:
Descartada porque genera latencia innecesaria en la UI y sobrecarga al servidor con peticiones repetitivas cuando los datos ya fueron recibidos en el payload inicial.
