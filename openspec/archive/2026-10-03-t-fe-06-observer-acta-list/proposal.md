# Proposal — T-FE-06: Observer: Flujo de JRVs Asignadas, Dignidades y Consulta de Actas

## Qué se construye
Flujo guiado para el veedor electoral (OBSERVER) en territorio Santa Elena 2027:
1. **Mis JRVs Asignadas (`/observer/mis-jrvs`)**: visualización de las mesas de votación asignadas al veedor, con métricas de progreso (pendientes vs completadas) y filtro por estado.
2. **Dignidades por JRV (`/observer/jrv/:jrvId/cargos`)**: desglose de dignidades electorales (Alcalde, Prefecto, Concejales Urbanos/Rurales, Vocales Parroquiales) con el estado de registro de cada acta.
3. **Servicios de Consulta y Disputas**: integración con los endpoints de actas registradas (`GET /api/v1/observer/mis-actas`) y actas que requieren subsanación urgente (`GET /api/v1/observer/correcciones-pendientes`).

## Por qué
- En el operativo de campo, un veedor atiende 1 o más JRVs y debe registrar múltiples dignidades en orden secuencial según el CNE cierra el conteo de cada urna.
- Evita que el veedor intente ingresar dos veces la misma dignidad en una mesa.
- Permite identificar de inmediato cuando un coordinador marca un acta como `EN_DISPUTA` para proceder a su corrección.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Vistas | `MisJrvsPage` (`/observer/mis-jrvs`), `JrvCargosPage` (`/observer/jrv/:jrvId/cargos`) | Registro detallado de votos (T-FE-07) |
| API | `GET /api/v1/observer/jrvs`, `GET /api/v1/observer/jrv/{jrvId}/cargos`, `GET /api/v1/observer/mis-actas`, `GET /api/v1/observer/correcciones-pendientes` | Endpoints de coordinador / master |
| Métricas | Contadores de JRVs totales, pendientes y completadas | Agregaciones provinciales (Dashboard) |
| Filtros | Filtro en cliente por pestañas: `TODAS`, `PENDIENTES`, `COMPLETADAS` | Paginación pesada en servidor |
| Navegación | JRV -> Dignidades -> Registro de Acta (`/observer/jrv/:jrvId/cargo/:cargoId/registrar`) | Saltos directos sin contexto de mesa |

## Criterio de aceptación

- [ ] `MisJrvsPage` carga en `/observer/mis-jrvs` consumiendo `GET /api/v1/observer/jrvs`.
- [ ] Pestañas de filtro (`TODAS`, `PENDIENTES`, `COMPLETADAS`) segregan mesas según `cargosPendientes`.
- [ ] Al seleccionar una JRV, navega a `/observer/jrv/:jrvId/cargos` y consume `GET /api/v1/observer/jrv/{jrvId}/cargos`.
- [ ] La lista de dignidades refleja claramente si el acta no está registrada (`sinRegistrar`) o si ya fue enviada/aprobada/en disputa.
- [ ] Si el acta está pendiente o en borrador, el botón permite iniciar/continuar el registro en `T-FE-07`.
- [ ] Manejo de estados de carga (`LoadingStateComponent`) y error con reintento (`ErrorStateComponent`).

## Fuera de alcance

- Formulario de votos y fotos (cubierto por T-FE-07 y T-FE-08).
- Modo offline local y sincronización diferida (cubierto por T-FE-09).

## Dependencias

- T-FE-02 (Rutas protegidas con `roleGuard(['OBSERVER', 'MASTER'])`).
- T-FE-03 / T-FE-04 (Autenticación JWT con token Bearer en `environment.apiUrl = '/api/v1'`).
