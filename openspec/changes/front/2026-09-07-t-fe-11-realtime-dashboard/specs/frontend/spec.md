# Spec — T-FE-11: Dashboard Reactivo en Tiempo Real e Integración REST

## Escenarios

### S1 — Carga inicial consolidada desde backend

```gherkin
GIVEN    usuario con rol ADMIN, MASTER o COORDINATOR autenticado
WHEN     navega a /dashboard
THEN     DashboardPage realiza llamadas en paralelo a:
         - GET /api/v1/dashboard/resumen
         - GET /api/v1/dashboard/resultados
         - GET /api/v1/dashboard/progreso
         - GET /api/v1/dashboard/novedades
AND      despliega las tarjetas de métricas, barras cantonales y novedades
```

### S2 — Evento Realtime dispara invalidación reactiva debounced

```gherkin
GIVEN    dashboard cargado y canal WebSocket de Supabase suscrito a tabla acta
WHEN     un veedor envía una nueva acta o un coordinador aprueba un acta
THEN     Supabase Realtime emite evento INSERT o UPDATE
AND      el frontend amortigua eventos mediante debounceTime(500)
AND      ejecuta una recarga limpia de los endpoints REST del backend
AND      los gráficos y números se actualizan reflejando los nuevos totales calculados por el servidor
```

### S3 — Exclusión de actas anuladas en métricas

```gherkin
GIVEN    un acta es ANULADA por el MASTER
WHEN     el dashboard recibe el evento de actualización
THEN     el backend la excluye automáticamente de las sumas de votos y actas activas
AND      el frontend refleja la disminución de votos sin alterar la consistencia aritmética
```

### S4 — Desconexión limpia al salir del Dashboard

```gherkin
GIVEN    usuario en /dashboard con suscripción activa a WebSocket
WHEN     navega a otra sección del sistema
THEN     ngOnDestroy remueve los canales de Supabase y cancela las subscripciones
AND      no quedan procesos huérfanos ni fugas de memoria
```

## Garantías

- **G1:** Autoridad absoluta del backend sobre la suma de votos y porcentajes.
- **G2:** Uso de Supabase Realtime como gatillo reactivo de invalidación de caché.
- **G3:** Rutas canónicas `/api/v1/dashboard/*`.
