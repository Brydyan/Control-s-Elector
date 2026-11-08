# Proposal — T-BE-19: Registro de Incidencias Operativas de JRV sin Acta

## Qué se construye
Módulo para reportar y gestionar contingencias operativas en recintos y mesas electorales donde no es posible o aún no corresponde generar un acta de escrutinio:
- Entidad de dominio `IncidenciaJrv`:
  - `tipoIncidencia`: `JRV_NO_INSTALADA`, `IMPEDIMENTO_VEEDOR`, `URNA_VIOLENTADA`, `FALTA_PAPELETAS`, `CORTE_ENERGIA`, `DISTURBIOS_RECINTO`, `OTRO`.
  - `descripcion`: Relato de los hechos por parte del veedor.
  - `evidenciaUrl`: Enlace opcional a fotografía del hecho.
- Endpoints REST:
  - `POST /api/v1/observer/incidencias`: Reporte inmediato por parte del veedor de campo.
  - `GET /api/v1/coordinator/incidencias`: Monitoreo en tiempo real de alertas provinciales por cantón/parroquia.
- Migración Liquibase `023-create-incidencia-jrv.sql`.

## Por qué
- En las elecciones de Santa Elena, ocurren eventos previos o durante la jornada que impiden la votación o el escrutinio normal: mesas que no se instalan por inasistencia de miembros de junta, policías o delegados CNE que impiden el ingreso de veedores acreditados, o desmanes físicos en recintos.
- Si el sistema solo permite interactuar mediante la creación de un acta de escrutinio, estas anomalías quedan invisibles para la sala de guerra y los asesores jurídicos.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Entidad | `IncidenciaJrv` y enum `TipoIncidenciaJrv` | Modificación de resultados electorales |
| Endpoints | Reporte por veedor (`POST`) y consulta por coordinador (`GET`) | Chat interactivo en tiempo real |
| Persistencia | Tabla `incidencia_jrv` con FK a `jrv` y `usuario` | Procesamiento judicial |
| Auditoría | Registro de timestamp e IP de reporte | Logs en memoria |

## Criterio de aceptación

- [ ] Migración Liquibase genera tabla `incidencia_jrv` con índices foráneos.
- [ ] Veedor puede enviar `IncidenciaRequestDto` indicando `jrvId`, `tipoIncidencia` y `descripcion`.
- [ ] Endpoint de coordinador permite filtrar incidencias por `cantonId`, `tipo` o `jrvId`.
- [ ] Alertas quedan registradas con fecha y usuario que reportó.

## Fuera de alcance

- Geo-localización por GPS continuo.
