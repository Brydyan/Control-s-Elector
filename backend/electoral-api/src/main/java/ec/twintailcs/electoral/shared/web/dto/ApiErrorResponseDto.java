package ec.twintailcs.electoral.shared.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * DTO de respuesta de error global de la API (T-BE-22 Lote 0.1).
 *
 * <p>Producido por {@code GlobalExceptionHandler.body()} cuando una request falla. Es el contrato
 * uniforme para todos los errores HTTP (4xx/5xx). Documentado en OpenAPI para que los clients
 * generados vean una sola estructura.
 */
@Schema(
    name = "ApiErrorResponse",
    description =
        "Respuesta uniforme de error. Todas las respuestas no-2xx de la API usan esta estructura.",
    example =
        "{\n"
            + "  \"timestamp\": \"2026-09-27T10:00:00Z\",\n"
            + "  \"status\": 400,\n"
            + "  \"error\": \"Bad Request\",\n"
            + "  \"message\": \"La suma de votos no cuadra: candidatos=80 + blancos=5 + nulos=10 = 95, esperado 100\"\n"
            + "}")
public record ApiErrorResponseDto(
    @Schema(
            description = "Timestamp UTC en ISO-8601 (Instant.toString)",
            example = "2026-09-27T10:00:00Z")
        Instant timestamp,
    @Schema(description = "Código HTTP numérico", example = "400") int status,
    @Schema(
            description = "Razón HTTP textual (HttpStatus.getReasonPhrase)",
            example = "Bad Request")
        String error,
    @Schema(description = "Mensaje legible para el cliente", example = "Acta no encontrada: 123")
        String message,
    @Schema(
            description =
                "Campos con detalle del error (solo presente en errores de validación). "
                    + "Clave: nombre del campo; valor: descripción del fallo.")
        Map<String, String> fields) {}
