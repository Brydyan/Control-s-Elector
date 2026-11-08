# Tasks: T-BE-22 Documentación OpenAPI 3.1 & Swagger UI

## Lote 0: Fundaciones y Esquema de Error

- [x] 0.1 Crear `ApiErrorResponseDto.java` en `ec.twintailcs.electoral.shared.web.dto` con anotaciones `@Schema`.
- [x] 0.2 Actualizar `OpenApiConfig.java`:
  - Registrar las 7 etiquetas de dominio canónicas (`Auth`, `Administración`, `Configuración Electoral`, `Evidencias`, `Dashboard`, `Actas - Observador`, `Actas - Coordinador`).
  - Registrar `ApiErrorResponseDto` en Components.

---

## Lote 1: Auth, Menús y Administración (4 controladores / 14 endpoints)

- [x] 1.1 Anotar `AuthController.java`:
  - `@Tag(name = "Auth")`.
  - `@SecurityRequirements()` en `/login`, `/refresh`, `/logout`.
  - `@Operation` y `@ApiResponse` (200, 400, 401) en cada endpoint.
  - `@Operation` en `POST /register` documentando restricción a rol `MASTER`.
- [x] 1.2 Anotar `MenuController.java`:
  - `@Tag(name = "Auth")`.
  - `@Operation` y `@ApiResponse` en `GET /me`.
- [x] 1.3 Anotar `SesionController.java`:
  - `@Tag(name = "Administración")`.
  - Documentar `GET /`, `GET /{sesionId}`, `DELETE /{sesionId}` con roles `MASTER`.
- [x] 1.4 Anotar `UsuarioController.java`:
  - `@Tag(name = "Administración")`.
  - Documentar CRUD de usuarios (`GET`, `POST`, `PUT`, `DELETE`) y parámetros UUIDv7.
- [x] 1.5 Anotar DTOs del dominio Auth/Admin con `@Schema` (`LoginRequest`, `AuthResponse`, `CreateUsuarioRequest`, `UpdateUsuarioRequest`, `SesionResponseDto`, `UsuarioResponseDto`).

---

## Lote 2: Configuración Electoral y Geografía (4 controladores / 13 endpoints)

- [x] 2.1 Anotar `CantonController.java`:
  - `@Tag(name = "Configuración Electoral")`.
  - Documentar `GET /`.
- [x] 2.2 Anotar `CandidatoController.java`:
  - `@Tag(name = "Configuración Electoral")`.
  - Documentar CRUD completo (`GET`, `POST`, `PUT`, `DELETE`).
- [x] 2.3 Anotar `CargoController.java`:
  - `@Tag(name = "Configuración Electoral")`.
  - Documentar CRUD completo (`GET`, `POST`, `PUT`, `DELETE`).
- [x] 2.4 Anotar `PartidoController.java`:
  - `@Tag(name = "Configuración Electoral")`.
  - Documentar CRUD completo (`GET`, `POST`, `PUT`, `DELETE`).
- [x] 2.5 Anotar DTOs electorales con `@Schema` (`CantonResponse`, `CandidatoResponse`, `CargoResponse`, `PartidoResponse`).

---

## Lote 3: Evidencias y Dashboard (2 controladores / 10 endpoints)

- [x] 3.1 Anotar `EvidenciaController.java`:
  - `@Tag(name = "Evidencias")`.
  - Documentar carga multipart `POST /`, consulta `GET /{id}`, pre-signed URL `GET /{id}/download-url`, cambio de estado `PATCH /{id}/estado`, observaciones `PATCH /{id}/observaciones` y borrado `DELETE /{id}`.
- [x] 3.2 Anotar `DashboardController.java`:
  - `@Tag(name = "Dashboard")`.
  - Documentar `GET /resumen`, `GET /resultados`, `GET /progreso`, `GET /novedades`.
- [x] 3.3 Anotar DTOs de Evidencias y Dashboard con `@Schema` (`EvidenciaResponse`, `DownloadUrlResponse`, `ResumenActasDto`, `ResultadoCargoDto`, `ProgresoCantonDto`, `NovedadDto`).

---

## Lote 4: Actas e Incidencias (Observer & Coordinator) (2 controladores / 21 endpoints)

- [x] 4.1 Anotar `ObserverController.java`:
  - `@Tag(name = "Actas - Observador")`.
  - Documentar 11 endpoints (`mis-actas`, `correcciones-pendientes`, `jrvs`, `jrv/{id}/cargos`, `actas`, `actas/borrador`, `actas/{id}/enviar`, `actas/{id}/corregir`, `actas/{id}/imagen`, `incidencias`, `actas/{id}`).
- [x] 4.2 Anotar `CoordinatorController.java`:
  - `@Tag(name = "Actas - Coordinador")`.
  - Documentar 10 endpoints (`actas`, `actas/{id}`, `historial`, `disputar`, `corregir`, `aprobar`, anulación `DELETE /actas/{id}`, `actas/{id}/imagen`, `incidencias`, `expediente-pdf`).
- [x] 4.3 Anotar DTOs de Actas e Incidencias con `@Schema` (`ActaResponseDto`, `ActaRequestDto`, `ActaDetalleCompletoDto`, `HistorialActaDto`, `IncidenciaResponseDto`, etc.).

---

## Lote 5: Verificación, Formato y Calidad

- [x] 5.1 Ejecutar `./mvnw spotless:apply` para formatear el código bajo el estándar del proyecto.
- [x] 5.2 Ejecutar `./mvnw test` asegurando que los 318 tests se mantienen en VERDE (0 regresiones).
- [x] 5.3 Verificar compilación limpia con `./mvnw clean compile`.
