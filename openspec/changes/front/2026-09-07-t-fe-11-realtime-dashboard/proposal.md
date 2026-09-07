# Proposal — T-FE-11: Dashboard en Tiempo Real con Supabase Realtime

## Qué se construye
Dashboard que se actualiza en tiempo real. Gráficos de estadísticas sin recargar la página.

Usa Supabase Realtime (WebSocket) para recibir cambios en tabla `acta`.

## Por qué
- Coordinator/Admin ven actualizaciones al instante.
- No requiere recargar o polling.
- WebSocket es eficiente.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Realtime | Supabase Realtime subscriber | server-sent events |
| Gráficos | Pie (por estado), Bar (por cargo, por cantón) | time-series |
| Datos | SUM votantes, actas aprobadas, en disputa | comparativa período |
| Auto-refresh | WebSocket → Observable → chart.update() | manual refresh |

## Criterio de aceptación

- [ ] DashboardComponent cargado en /dashboard
- [ ] Supabase Realtime suscripto a tabla `acta`
- [ ] Cambios en `acta` → recibe evento → gráficos actualizan
- [ ] Gráficos: Pie (estado), Bar (cargo), estadísticas texto
- [ ] Test: mock Realtime, simular INSERT, verificar que chart se actualiza

## Fuera de alcance

- Filtros complejos
- Exportación
- Predicciones/ML
- Caching sofisticado

## Dependencias

- T-FE-05 (login para autenticación Supabase)
- Chart library (ng2-charts, Chart.js)

## Decisiones a tomar

**D1 — Chart library:** Usar ng2-charts + Chart.js (estándar Angular).

**D2 — qué eventos escuchar:** INSERT, UPDATE en tabla `acta` (no DELETE, actas nunca se borran).
