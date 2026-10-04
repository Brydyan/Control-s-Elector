# Design — T-FE-11: Dashboard en Tiempo Real

## D1 — Chart Library

**Decisión (T-FE-11):** CSS cards + progress bars. MVP lightweight.

**Deferred to T-FE-12:** ng2-charts + Chart.js (sería estándar, soporte comunidad, pero scope creep para T-FE-11).

**Alternativa descartada en scope actual — Plotly, ECharts.** Más pesados aún.

## D2 — Qué eventos escuchar

**Decisión:** INSERT (nueva acta), UPDATE (cambio estado). No DELETE (actas immutable).

**Alternativa descartada — todos.** Suficiente con INSERT + UPDATE.

## D3 — Cálculos en frontend vs backend

**Decisión:** Backend. `DashboardService` en PostgreSQL calcula totales, porcentajes, agrupaciones. Frontend solo invalida caché + recarga endpoints REST con debounce.

**Por qué:** Evita desincronización, complejidad electorales (actas anuladas), saturación móvil.

**Alternativa descartada — frontend recalcula en memoria.** Propenso a bugs de negocio electoral, lógica duplicada.

## D4 — Endpoints oficiales de Dashboard

**Decisión:** Consume 4 endpoints en paralelo (forkJoin):
- `GET /api/v1/dashboard/resumen` — totales por estado
- `GET /api/v1/dashboard/resultados` — votos consolidados
- `GET /api/v1/dashboard/progreso` — avance por cantón
- `GET /api/v1/dashboard/novedades` — actas en disputa

Al cargar (estado inicial), luego Realtime invalida caché + recarga con debounce 500ms.

**Alternativa descartada — solo Realtime.** Usuarios que abren dashboard por primera vez ven vacío hasta primer evento.
