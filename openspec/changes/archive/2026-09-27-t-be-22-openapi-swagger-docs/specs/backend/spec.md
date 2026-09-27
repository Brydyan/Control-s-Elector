# Spec: T-BE-22 Documentación OpenAPI 3.1 & Swagger UI

## Requerimientos

### REQ-OAS-01: Taxonomía de Tags Canónicos
El sistema debe agrupar los 12 controladores en exactamente 7 etiquetas descriptivas definidas en `OpenApiConfig`:
1. `Auth`: Autenticación, sesión y menús dinámicos.
2. `Administración`: Gestión de usuarios y supervisión de sesiones.
3. `Configuración Electoral`: Catálogos geográficos y electorales.
4. `Evidencias`: Gestión de archivos fotográficos en almacenamiento S3.
5. `Dashboard`: Métricas y consolidación electoral de solo lectura.
6. `Actas - Observador`: Flujo operativo del veedor en JRV.
7. `Actas - Coordinador`: Flujo de supervisión, resolución de disputas y auditoría.

### REQ-OAS-02: Esquema Canónico de Respuestas de Error
El sistema debe exponer el contrato de error emitido por `GlobalExceptionHandler` mediante un DTO documentado con anotaciones `@Schema`:
- Record: `ApiErrorResponseDto(String timestamp, int status, String error, String message, Map<String, String> fields)`.
- Debe ser referenciado en todas las anotaciones `@ApiResponse` de error 400, 401, 403, 404 y 409.

### REQ-OAS-03: Exclusión de Seguridad en Rutas Públicas
Los endpoints `/api/v1/auth/login`, `/api/v1/auth/refresh` y `/api/v1/auth/logout` deben estar anotados con `@SecurityRequirements()` para indicar que no requieren autenticación Bearer JWT en Swagger UI.

### REQ-OAS-04: Metadatos de Operación en Todos los Endpoints
Cada uno de los 58 endpoints debe contar con:
- `@Operation.summary`: Resumen conciso en infinitivo (máximo 60 caracteres).
- `@Operation.description`: Descripción funcional detallada que incluya el rol RBAC requerido (`MASTER`, `COORDINATOR`, `OBSERVER`, `ADMIN`).
- `@ApiResponse`: Declaración de código de éxito (`200 OK`, `201 Created` o `204 No Content`) y códigos de error pertinentes.

### REQ-OAS-05: Documentación de Cargas Multipart y Respuestas Binarias
- Los endpoints de subida de fotos (`/api/v1/evidencias`, `/api/v1/coordinator/actas/{actaId}/imagen`, `/api/v1/observer/actas/{actaId}/imagen`) deben especificar `consumes = MediaType.MULTIPART_FORM_DATA_VALUE` y documentar la parte del archivo (`imagen` o `file`) con formato binario y límite de 10MB.
- El endpoint de descarga de expediente CNE (`/api/v1/coordinator/actas/{actaId}/expediente-pdf`) debe documentar `produces = "application/pdf"` con esquema binario para descarga directa.

### REQ-OAS-06: Documentación de Parámetros y Schemas de DTOs
- Todos los `@PathVariable` y `@RequestParam` deben incluir `@Parameter(description = "...", example = "...")`.
- Los DTOs de Request y Response principales deben contener anotaciones `@Schema` en sus atributos representativos.

---

## Matriz de Especificación Exhaustiva de Endpoints (58 Endpoints)

### Dominio 1: Auth (`ec.twintailcs.electoral.auth.web`) — Tag: `Auth`
| Método | Ruta | Seguridad | Roles | Retorno Exitoso | Códigos de Error |
|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Pública (`@SecurityRequirements`) | Cualquiera | `200 AuthResponse` | `400`, `401` |
| `POST` | `/api/v1/auth/refresh` | Pública (`@SecurityRequirements`) | Cualquiera | `200 AuthResponse` | `400`, `401` |
| `POST` | `/api/v1/auth/logout` | Pública (`@SecurityRequirements`) | Cualquiera | `204 No Content` | `400` |
| `GET` | `/api/v1/auth/me` | Bearer JWT | Autenticado | `200 UsuarioActual` | `401` |
| `POST` | `/api/v1/auth/register` | Bearer JWT | `MASTER` | `201 UsuarioResponseDto` | `400`, `401`, `403`, `409` |
| `GET` | `/api/v1/menus/me` | Bearer JWT | Autenticado | `200 MenuResponse` | `401` |

### Dominio 2: Administración (`ec.twintailcs.electoral.auth.web`) — Tag: `Administración`
| Método | Ruta | Seguridad | Roles / Permisos | Retorno Exitoso | Códigos de Error |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/sesiones` | Bearer JWT | `MASTER` / `sesion:list` | `200 PagedResponse<SesionResponseDto>` | `401`, `403` |
| `GET` | `/api/v1/admin/sesiones/{sesionId}` | Bearer JWT | `MASTER` / `sesion:list` | `200 SesionResponseDto` | `401`, `403`, `404` |
| `DELETE` | `/api/v1/admin/sesiones/{sesionId}` | Bearer JWT | `MASTER` / `sesion:revoke` | `204 No Content` | `401`, `403`, `404` |
| `GET` | `/api/v1/admin/usuarios` | Bearer JWT | `MASTER` / `usuario:read` | `200 PagedResponse<UsuarioResponseDto>` | `401`, `403` |
| `GET` | `/api/v1/admin/usuarios/{publicId}` | Bearer JWT | `MASTER` / `usuario:read` | `200 UsuarioResponseDto` | `401`, `403`, `404` |
| `POST` | `/api/v1/admin/usuarios` | Bearer JWT | `MASTER` / `usuario:create` | `201 UsuarioResponseDto` | `400`, `401`, `403`, `409` |
| `PUT` | `/api/v1/admin/usuarios/{publicId}` | Bearer JWT | `MASTER` / `usuario:update` | `200 UsuarioResponseDto` | `400`, `401`, `403`, `404`, `409` |
| `DELETE` | `/api/v1/admin/usuarios/{publicId}` | Bearer JWT | `MASTER` / `usuario:delete` | `204 No Content` | `401`, `403`, `404` |

### Dominio 3: Configuración Electoral y Geografía — Tag: `Configuración Electoral`
| Método | Ruta | Seguridad | Roles / Permisos | Retorno Exitoso | Códigos de Error |
|---|---|---|---|---|---|
| `GET` | `/api/v1/configuracion/cantones` | Bearer JWT | Autenticado | `200 List<CantonResponse>` | `401` |
| `GET` | `/api/v1/configuracion/candidatos` | Bearer JWT | Autenticado | `200 List<CandidatoResponse>` | `401` |
| `POST` | `/api/v1/configuracion/candidatos` | Bearer JWT | `MASTER` | `201 CandidatoResponse` | `400`, `401`, `403` |
| `PUT` | `/api/v1/configuracion/candidatos/{id}` | Bearer JWT | `MASTER` | `200 CandidatoResponse` | `400`, `401`, `403`, `404` |
| `DELETE` | `/api/v1/configuracion/candidatos/{id}` | Bearer JWT | `MASTER` | `204 No Content` | `401`, `403`, `404` |
| `GET` | `/api/v1/configuracion/cargos` | Bearer JWT | Autenticado | `200 List<CargoResponse>` | `401` |
| `POST` | `/api/v1/configuracion/cargos` | Bearer JWT | `MASTER` | `201 CargoResponse` | `400`, `401`, `403` |
| `PUT` | `/api/v1/configuracion/cargos/{id}` | Bearer JWT | `MASTER` | `200 CargoResponse` | `400`, `401`, `403`, `404` |
| `DELETE` | `/api/v1/configuracion/cargos/{id}` | Bearer JWT | `MASTER` | `204 No Content` | `401`, `403`, `404` |
| `GET` | `/api/v1/configuracion/partidos` | Bearer JWT | Autenticado | `200 List<PartidoResponse>` | `401` |
| `POST` | `/api/v1/configuracion/partidos` | Bearer JWT | `MASTER` | `201 PartidoResponse` | `400`, `401`, `403` |
| `PUT` | `/api/v1/configuracion/partidos/{id}` | Bearer JWT | `MASTER` | `200 PartidoResponse` | `400`, `401`, `403`, `404` |
| `DELETE` | `/api/v1/configuracion/partidos/{id}` | Bearer JWT | `MASTER` | `204 No Content` | `401`, `403`, `404` |

### Dominio 4: Evidencias y Storage — Tag: `Evidencias`
| Método | Ruta | Seguridad | Roles / Permisos | Retorno Exitoso | Códigos de Error |
|---|---|---|---|---|---|
| `POST` | `/api/v1/evidencias` | Bearer JWT | `evidencia:upload` | `201 EvidenciaResponse` | `400`, `401`, `403` |
| `GET` | `/api/v1/evidencias/{id}` | Bearer JWT | `evidencia:read` | `200 EvidenciaResponse` | `401`, `403`, `404` |
| `GET` | `/api/v1/evidencias/{id}/download-url` | Bearer JWT | `evidencia:read` | `200 DownloadUrlResponse` | `401`, `403`, `404` |
| `PATCH` | `/api/v1/evidencias/{id}/estado` | Bearer JWT | `evidencia:review` | `200 EvidenciaResponse` | `400`, `401`, `403`, `404` |
| `DELETE` | `/api/v1/evidencias/{id}` | Bearer JWT | `evidencia:delete` | `204 No Content` | `401`, `403`, `404` |
| `PATCH` | `/api/v1/evidencias/{id}/observaciones` | Bearer JWT | Dueño (`OBSERVER`) | `200 EvidenciaResponse` | `400`, `401`, `403`, `404` |

### Dominio 5: Dashboard Analítico — Tag: `Dashboard`
| Método | Ruta | Seguridad | Roles / Permisos | Retorno Exitoso | Códigos de Error |
|---|---|---|---|---|---|
| `GET` | `/api/v1/dashboard/resumen` | Bearer JWT | `ADMIN`, `COORDINATOR`, `MASTER` | `200 ResumenActasDto` | `401`, `403` |
| `GET` | `/api/v1/dashboard/resultados` | Bearer JWT | `ADMIN`, `COORDINATOR`, `MASTER` | `200 List<ResultadoCargoDto>` | `401`, `403` |
| `GET` | `/api/v1/dashboard/progreso` | Bearer JWT | `ADMIN`, `COORDINATOR`, `MASTER` | `200 List<ProgresoCantonDto>` | `401`, `403` |
| `GET` | `/api/v1/dashboard/novedades` | Bearer JWT | `ADMIN`, `COORDINATOR`, `MASTER` | `200 List<NovedadDto>` | `401`, `403` |

### Dominio 6: Actas - Observador — Tag: `Actas - Observador`
| Método | Ruta | Seguridad | Roles / Permisos | Retorno Exitoso | Códigos de Error |
|---|---|---|---|---|---|
| `GET` | `/api/v1/observer/mis-actas` | Bearer JWT | `OBSERVER` | `200 List<ActaResponseDto>` | `401`, `403` |
| `GET` | `/api/v1/observer/correcciones-pendientes` | Bearer JWT | `OBSERVER` | `200 List<ActaResponseDto>` | `401`, `403` |
| `GET` | `/api/v1/observer/jrvs` | Bearer JWT | `OBSERVER` | `200 List<JrvConEstadoDto>` | `401`, `403` |
| `GET` | `/api/v1/observer/jrv/{jrvId}/cargos` | Bearer JWT | `OBSERVER` | `200 List<CargoConEstadoDto>` | `401`, `403`, `404` |
| `POST` | `/api/v1/observer/actas` | Bearer JWT | `OBSERVER` | `201 ActaResponseDto` | `400`, `401`, `403`, `409` |
| `POST` | `/api/v1/observer/actas/borrador` | Bearer JWT | `OBSERVER` | `201 ActaResponseDto` | `400`, `401`, `403`, `409` |
| `POST` | `/api/v1/observer/actas/{actaId}/enviar` | Bearer JWT | `OBSERVER` | `200 ActaResponseDto` | `400`, `401`, `403`, `404`, `409` |
| `PUT` | `/api/v1/observer/actas/{actaId}/corregir` | Bearer JWT | `OBSERVER` | `200 ActaResponseDto` | `400`, `401`, `403`, `404`, `409` |
| `POST` | `/api/v1/observer/actas/{actaId}/imagen` | Bearer JWT | `OBSERVER` | `200 OK` | `400`, `401`, `403`, `404` |
| `POST` | `/api/v1/observer/incidencias` | Bearer JWT | `OBSERVER` | `201 IncidenciaResponseDto` | `400`, `401`, `403` |
| `GET` | `/api/v1/observer/actas/{actaId}` | Bearer JWT | `OBSERVER` (dueño) | `200 ActaDetalleCompletoDto` | `401`, `403`, `404` |

### Dominio 7: Actas - Coordinador — Tag: `Actas - Coordinador`
| Método | Ruta | Seguridad | Roles / Permisos | Retorno Exitoso | Códigos de Error |
|---|---|---|---|---|---|
| `GET` | `/api/v1/coordinator/actas` | Bearer JWT | `COORDINATOR`, `MASTER`, `ADMIN` | `200 PagedResponse<ActaResponseDto>` | `401`, `403` |
| `GET` | `/api/v1/coordinator/actas/{actaId}` | Bearer JWT | `COORDINATOR`, `MASTER`, `ADMIN` | `200 ActaDetalleCompletoDto` | `401`, `403`, `404` |
| `GET` | `/api/v1/coordinator/actas/{actaId}/historial` | Bearer JWT | `COORDINATOR`, `MASTER`, `ADMIN` | `200 List<HistorialActaDto>` | `401`, `403`, `404` |
| `PUT` | `/api/v1/coordinator/actas/{actaId}/disputar` | Bearer JWT | `COORDINATOR`, `MASTER` | `200 ActaResponseDto` | `400`, `401`, `403`, `404`, `409` |
| `PUT` | `/api/v1/coordinator/actas/{actaId}/corregir` | Bearer JWT | `COORDINATOR`, `MASTER` | `200 ActaResponseDto` | `400`, `401`, `403`, `404`, `409` |
| `PUT` | `/api/v1/coordinator/actas/{actaId}/aprobar` | Bearer JWT | `COORDINATOR`, `MASTER` | `200 ActaResponseDto` | `400`, `401`, `403`, `404`, `409` |
| `DELETE` | `/api/v1/coordinator/actas/{actaId}` | Bearer JWT | `acta:anular` | `204 No Content` | `400`, `401`, `403`, `404` |
| `POST` | `/api/v1/coordinator/actas/{actaId}/imagen` | Bearer JWT | `COORDINATOR`, `MASTER` | `200 OK` | `400`, `401`, `403`, `404` |
| `GET` | `/api/v1/coordinator/incidencias` | Bearer JWT | `COORDINATOR`, `MASTER`, `ADMIN` | `200 List<IncidenciaResponseDto>` | `401`, `403` |
| `GET` | `/api/v1/coordinator/actas/{actaId}/expediente-pdf` | Bearer JWT | `COORDINATOR`, `MASTER`, `ADMIN` | `200 application/pdf` | `401`, `403`, `404` |

---

## Escenarios de Verificación

### Escenario 1: Navegación y agrupamiento en Swagger UI
- **GIVEN** la aplicación `electoral-api` en ejecución.
- **WHEN** un cliente consulta `/v3/api-docs`.
- **THEN** el documento OpenAPI 3.1 contiene las 7 etiquetas canónicas y 0 etiquetas por defecto en kebab-case (`coordinator-controller`).
- **AND** cada operación está asignada a su etiqueta de dominio correspondiente.

### Escenario 2: Endpoint público sin exigencia de Bearer JWT
- **GIVEN** el endpoint `POST /api/v1/auth/login` en Swagger UI.
- **WHEN** el usuario visualiza los detalles de la operación.
- **THEN** no aparece el icono de candado ni la directiva `bearer-jwt`.
- **AND** se documentan las respuestas HTTP 200 con `AuthResponse` y HTTP 401 con `ApiErrorResponseDto`.

### Escenario 3: Documentación de endpoints protegidos con RBAC
- **GIVEN** el endpoint `DELETE /api/v1/coordinator/actas/{actaId}`.
- **WHEN** se examina en OpenAPI spec.
- **THEN** la descripción explicita que requiere autoridad `acta:anular` (rol `MASTER` o `COORDINATOR`).
- **AND** se documentan respuestas HTTP 204, HTTP 401, HTTP 403 y HTTP 404.

### Escenario 4: Formato de carga de imagen de evidencia
- **GIVEN** el endpoint `POST /api/v1/evidencias`.
- **WHEN** se genera el esquema OpenAPI.
- **THEN** el request body describe `multipart/form-data` con un parámetro binario de archivo.
- **AND** la respuesta exitosa documenta `EvidenciaResponse`.

### Escenario 5: Cero regresiones en tests automatizados
- **GIVEN** el código con las nuevas anotaciones OpenAPI.
- **WHEN** se ejecuta `./mvnw test`.
- **THEN** los 318 tests pasan en verde (0 errores, 0 fallos).
- **AND** `./mvnw spotless:check` pasa sin violaciones de formato.
