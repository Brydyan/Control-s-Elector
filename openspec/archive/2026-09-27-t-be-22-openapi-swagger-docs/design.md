# Design: T-BE-22 Documentación OpenAPI 3.1 & Swagger UI

## Arquitectura y Componentes

### 1. Modelo de Error Canónico (`ApiErrorResponseDto`)
Actualmente `GlobalExceptionHandler.body(...)` construye un mapa `LinkedHashMap<String, Object>` con las llaves:
- `timestamp`: String ISO-8601.
- `status`: int (ej. 400, 401, 403, 404, 409, 500).
- `error`: String (ej. "Bad Request", "Unauthorized", "Not Found").
- `message`: String (mensaje de negocio o validación).
- `fields`: Map<String, String> (opcional, errores por atributo).

Se crea el record:
```java
package ec.twintailcs.electoral.shared.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Estructura estándar de respuesta de error de la API electoral")
public record ApiErrorResponseDto(
    @Schema(description = "Marca de tiempo UTC en formato ISO-8601", example = "2026-09-27T16:00:00Z")
    String timestamp,
    @Schema(description = "Código de estado HTTP", example = "400")
    int status,
    @Schema(description = "Razón canónica del estado HTTP", example = "Bad Request")
    String error,
    @Schema(description = "Mensaje explicativo del error de negocio o validación", example = "Credenciales inválidas")
    String message,
    @Schema(description = "Desglose opcional de errores por campo de entrada", example = "{\"email\": \"formato inválido\"}")
    Map<String, String> fields) {}
```

### 2. Configuración Programática (`OpenApiConfig.java`)
Se actualizan las etiquetas del OpenAPI Bean:
1. `Auth` -> "Login, refresh, logout, me y árbol de menús dinámico."
2. `Administración` -> "Gestión de usuarios y revocación de sesiones concurrentes (MASTER)."
3. `Configuración Electoral` -> "Catálogos de cantones, cargos, partidos políticos y candidatos."
4. `Evidencias` -> "Subida, descarga, estados y auditoría de actas físicas en S3/MinIO."
5. `Dashboard` -> "Métricas consolidadas, avance cantonal y novedades en tiempo real."
6. `Actas - Observador` -> "Registro, borradores, correcciones, fotos e incidencias por veedor en JRV."
7. `Actas - Coordinador` -> "Revisión, disputa, corrección, aprobación, anulación y expediente PDF."

### 3. Patrón de Anotación en Controladores
A nivel de clase:
```java
@Tag(name = "Auth", description = "Endpoints de autenticación y gestión de sesiones")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController { ... }
```

A nivel de método público:
```java
@SecurityRequirements()
@Operation(
    summary = "Iniciar sesión",
    description = "Autentica credenciales y emite access token JWT + cookie refresh_token httpOnly.")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Autenticación exitosa", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
    @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(responseCode = "401", description = "Credenciales incorrectas o usuario inactivo", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
})
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) { ... }
```

A nivel de método protegido con RBAC:
```java
@Operation(
    summary = "Aprobar acta electoral",
    description = "Aprueba formalmente un acta en estado ENVIADA o CORREGIDA. Requiere rol COORDINATOR o MASTER.")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Acta aprobada", content = @Content(schema = @Schema(implementation = ActaResponseDto.class))),
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(responseCode = "403", description = "Permisos insuficientes", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(responseCode = "404", description = "Acta no encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(responseCode = "409", description = "El acta no está en estado aprobable", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
})
@PutMapping("/actas/{actaId}/aprobar")
public ResponseEntity<ActaResponseDto> aprobar(...) { ... }
```

### 4. Soporte Multipart y Binary
Para subida de fotos de actas:
```java
@Operation(
    summary = "Subir fotografía del acta física",
    description = "Sube imagen JPG/PNG/WEBP de hasta 10MB asociada al acta. Convierte y almacena en S3.")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Foto subida y vinculada exitosamente"),
    @ApiResponse(responseCode = "400", description = "Archivo ausente o excede el límite de tamaño", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(responseCode = "403", description = "No autorizado para subir evidencia a esta JRV", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
})
@PostMapping(value = "/actas/{actaId}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<Void> subirImagen(
    @Parameter(description = "ID numérico del acta electoral") @PathVariable Long actaId,
    @Parameter(description = "Archivo de imagen (máx 10MB)") @RequestPart("imagen") MultipartFile imagen) { ... }
```

Para descarga de PDF:
```java
@Operation(
    summary = "Descargar expediente de impugnación CNE en PDF",
    description = "Genera y descarga el expediente completo del acta en formato PDF para trámite ante el CNE.")
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Documento PDF generado exitosamente",
        content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))),
    @ApiResponse(responseCode = "404", description = "Acta no encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
})
@GetMapping(value = "/actas/{actaId}/expediente-pdf", produces = "application/pdf")
public ResponseEntity<byte[]> descargarExpedientePdf(...) { ... }
```
