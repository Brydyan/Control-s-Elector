# Design — T-FE-11: Dashboard en Tiempo Real

## D1 — Chart Library

**Decisión:** ng2-charts + Chart.js. Estándar en Angular, soporte comunidad.

**Alternativa descartada — Plotly, ECharts.** Más pesados, overkill para MVP.

## D2 — Qué eventos escuchar

**Decisión:** INSERT (nueva acta), UPDATE (cambio estado). No DELETE (actas immutable).

**Alternativa descartada — todos.** Suficiente con INSERT + UPDATE.

## D3 — Cálculos en frontend vs backend

**Decisión:** Frontend. Recibe cambio en Realtime, actualiza chart en memoria.

**Alternativa descartada — backend calcula y envía agregados.** Más tráfico.

## D4 — Inicialización: GET o esperar primer evento

**Decisión:** GET /api/resultados/consolidado al cargar (estado inicial), luego Realtime para cambios.

**Alternativa descartada — solo Realtime.** Usuarios que abren dashboard por primera vez ven vacío hasta primer evento.
