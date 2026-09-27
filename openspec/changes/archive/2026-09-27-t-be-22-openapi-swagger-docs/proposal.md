# Proposal: T-BE-22 Documentación OpenAPI 3.1 & Swagger UI

## Intent

La API REST `electoral-api` cuenta con 12 controladores y 58 endpoints que actualmente carecen de anotaciones OpenAPI (`@Tag`, `@Operation`, `@ApiResponse`, `@Parameter`, `@Schema`). Swagger UI (`/swagger-ui.html`) renderiza nombres de clase en kebab-case (`coordinator-controller`), nombres de métodos Java crudos (`verActaDetalle`), no documenta los códigos de error HTTP (400, 401, 403, 404, 409), no describe los campos de los DTOs y exige falsamente token Bearer JWT en rutas de autenticación públicas (`/login`, `/refresh`, `/logout`).

Esta propuesta busca formalizar y enriquecer el contrato OpenAPI 3.1 en todo el backend, proveyendo documentación exhaustiva, descriptiva y tipada para frontend, equipos de QA y consumidores de la API, sin modificar ninguna regla ni comportamiento de negocio.

## Scope

### In Scope
- Actualizar `OpenApiConfig.java` para consolidar las 7 etiquetas de dominio canónicas y declarar el esquema global de error.
- Crear el DTO canónico de error `ApiErrorResponseDto` con `@Schema` representativo de las respuestas emitidas por `GlobalExceptionHandler`.
- Anotar los 12 controladores con `@Tag` a nivel de clase.
- Anotar los 58 endpoints con `@Operation` (resumen, descripción en Markdown y rol RBAC requerido).
- Declarar `@SecurityRequirements` vacío en los endpoints públicos (`/login`, `/refresh`, `/logout`).
- Anotar las respuestas esperadas con `@ApiResponse` y `@ApiResponses` (200, 201, 204, 400, 401, 403, 404, 409).
- Anotar parámetros de ruta (`@PathVariable`) y query (`@RequestParam`) con `@Parameter` (descripción, formato UUIDv7, ejemplos).
- Documentar payloads binarios y multipart (`multipart/form-data` para fotos de actas y `application/pdf` para expedientes CNE).
- Anotar los DTOs principales de Request y Response con `@Schema` (descripciones de campos y valores de ejemplo).
- Verificar que la suite de tests unitarios e integrados (`./mvnw test`) y el linter de formato (`./mvnw spotless:check`) se mantengan en 100% VERDE.

### Out of Scope
- Modificación de lógica de negocio en servicios, casos de uso o repositorios.
- Modificación de rutas URL, métodos HTTP o códigos de estado devueltos.
- Alteración de entidades JPA o migraciones de base de datos Liquibase.

## Capabilities

### New Capabilities
- `backend-openapi-docs`: Documentación de contratos REST OpenAPI 3.1 y Swagger UI para todos los dominios de la API.

### Modified Capabilities
- Ninguna existente en `openspec/specs/` cambia de requerimiento de negocio.

## Approach

1. **Taxonomía unificada**: Organizar los 12 controladores en 7 tags lógicos:
   - `Auth`: Autenticación, sesión del usuario actual y árbol de menús dinámico.
   - `Administración`: Gestión de cuentas de usuario (`MASTER`) y supervisión de sesiones concurrentes.
   - `Configuración Electoral`: Catálogo de cantones, cargos, partidos políticos y candidatos.
   - `Evidencias`: Subida, pre-signed URLs y ciclo de vida de fotos de actas en MinIO/S3.
   - `Dashboard`: Estadísticas, consolidado de votos y novedades en tiempo real.
   - `Actas - Observador`: Registro, borradores, corrección, fotos e incidencias en JRV.
   - `Actas - Coordinador`: Aprobación, disputas, correcciones, anulación y expediente PDF de actas.
2. **Esquema de error compartido**: Registrar `ApiErrorResponseDto` en OpenAPI Components para que todos los `@ApiResponse(4xx)` apunten al mismo schema uniforme `{ timestamp, status, error, message, fields }`.
3. **Public endpoints**: Explicitar `@SecurityRequirements` para que el botón "Authorize" de Swagger UI no interfiera con endpoints no autenticados.
4. **Ejecución por lotes**: Implementar en 4 lotes temáticos y verificar con `./mvnw test` y `./mvnw spotless:apply`.
