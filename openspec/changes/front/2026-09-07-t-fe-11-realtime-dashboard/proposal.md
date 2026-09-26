# Proposal — T-FE-11: Dashboard Reactivo en Tiempo Real e Integración REST

## Qué se construye
Dashboard analítico para directivos y coordinadores (`ADMIN`, `MASTER`, `COORDINATOR`) con métricas electorales consolidadas y refresco reactivo:
1. **Consumo de Endpoints Oficiales de Dashboard**:
   - `GET /api/v1/dashboard/resumen`: Totales de actas por estado (`borrador`, `enviada`, `enDisputa`, `corregida`, `aprobada`, `enObservacion`).
   - `GET /api/v1/dashboard/resultados`: Votos consolidados por candidato agrupados por dignidad/cargo electoral.
   - `GET /api/v1/dashboard/progreso`: Avance del escrutinio por cantón (Santa Elena, La Libertad, Salinas) calculando porcentaje de JRVs completadas.
   - `GET /api/v1/dashboard/novedades`: Listado de actas en disputa con tiempo de antigüedad en minutos.
2. **Autoridad de Cálculo en Backend**:
   - Toda la aritmética electoral, agregaciones provinciales y exclusión de actas anuladas son computadas por `DashboardService` en PostgreSQL.
3. **Refresco Reactivo mediante Supabase Realtime (Cache Invalidation)**:
   - Suscripción WebSocket a cambios en la tabla `acta` (INSERT / UPDATE).
   - Al recibir un evento, se dispara un refresco con debounce (500 ms) de los endpoints REST del backend, manteniendo los gráficos y métricas actualizados en vivo sin sobrecargar la red ni recalcular datos en el navegador.

## Por qué
- Calcular subtotales y porcentajes electorales en JavaScript en el navegador es propenso a desincronización, ignora reglas de negocio complejas (votos de actas anuladas o en observación) y satura dispositivos móviles.
- Utilizar Realtime como un invalidador de caché reactivo combina lo mejor de dos mundos: inmediatez visual WebSocket y consistencia transaccional ACID garantizada por el backend.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Vistas | `DashboardPage` (`/dashboard`) | Pantallas operativas de digitación |
| Endpoints | `/api/v1/dashboard/{resumen, resultados, progreso, novedades}` | Endpoints legacy `/api/resultados/*` |
| Realtime | WebSocket canal Supabase postgres_changes con debounce 500ms | Recálculo de totales electorales en el cliente |
| Gráficos | Tarjetas de métricas, barras de avance cantonal, desglose por estado | Modelos predictivos o proyecciones de tendencias |

## Criterio de aceptación

- [ ] Carga en paralelo los 4 endpoints oficiales de `/api/v1/dashboard/*` al montar la vista.
- [ ] Renderiza tarjetas de estado (Total, Enviadas, En Disputa, Aprobadas, etc.).
- [ ] Renderiza barras de progreso por cantón (`Santa Elena`, `La Libertad`, `Salinas`).
- [ ] Suscribe al canal Realtime para la tabla `acta`.
- [ ] Ante eventos de inserción/actualización de actas, invalida la caché y recarga los endpoints con debounce de 500 ms.
- [ ] Desuscribe canales y limpia timers en `ngOnDestroy` para evitar fugas de memoria.

## Fuera de alcance

- Simulación estadística o proyecciones de boca de urna.

## Dependencias

- Backend `DashboardController` (`/api/v1/dashboard/*`).
- Supabase Client configurado en el frontend.
