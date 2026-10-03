# Spec — T-FE-06: Observer: Flujo de JRVs Asignadas, Dignidades y Consulta de Actas

## Escenarios

### S1 — Cargar lista de JRVs asignadas

```gherkin
GIVEN    usuario con rol OBSERVER autenticado
WHEN     navega a /observer/mis-jrvs
THEN     MisJrvsPage inicializa
AND      llama a ObserverService.getMisJrvs() (GET /api/v1/observer/jrvs)
AND      muestra las tarjetas de JRVs con número, parroquia y contadores de cargos pendientes
```

### S2 — Filtrar JRVs por estado de avance

```gherkin
GIVEN    lista de JRVs cargada en MisJrvsPage
WHEN     usuario presiona la pestaña "PENDIENTES"
THEN     se muestran únicamente las JRVs con cargosPendientes > 0
WHEN     usuario presiona "COMPLETADAS"
THEN     se muestran únicamente las JRVs con cargosPendientes === 0
```

### S3 — Navegar a las dignidades de una JRV

```gherkin
GIVEN    lista de JRVs visible
WHEN     usuario hace click en una tarjeta de JRV
THEN     el router navega a /observer/jrv/:jrvId/cargos
```

### S4 — Cargar dignidades de la JRV seleccionada

```gherkin
GIVEN    usuario en /observer/jrv/:jrvId/cargos
WHEN     JrvCargosPage inicializa
THEN     llama a ObserverService.getCargosDeJrv(jrvId) (GET /api/v1/observer/jrv/:jrvId/cargos)
AND      muestra cada dignidad (Alcalde, Prefecto, etc.) con su estado (registrado / sin registrar)
```

### S5 — Iniciar registro de un acta

```gherkin
GIVEN    lista de dignidades de la JRV
WHEN     usuario presiona "Registrar Acta" en una dignidad sin registrar
THEN     el router navega a /observer/jrv/:jrvId/cargo/:cargoId/registrar
```

### S6 — Manejo de errores de conexión con reintento

```gherkin
GIVEN    falla temporal de conectividad al consultar JRVs o dignidades
WHEN     el servicio retorna error HTTP 5xx o de red
THEN     se muestra ErrorStateComponent con mensaje amigable
AND      el botón "Reintentar" permite volver a solicitar los datos al backend
```

## Garantías

- **G1:** Peticiones HTTP dirigidas a `/api/v1/observer/*` con Bearer Token inyectado.
- **G2:** Segregación estricta de rutas con `roleGuard(['OBSERVER', 'MASTER'])`.
- **G3:** Mapeo de estados de acta compatible con el backend (`BORRADOR`, `ENVIADA`, `APROBADA`, `EN_DISPUTA`, `CORREGIDA`, `ANULADA`).
