package ec.twintailcs.electoral.evidencia.web.dto;

import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta pública de la API. NO expone la columna {@code version} (interna) ni el SHA-256 crudo
 * por ahora; solo metadata util para el FE.
 *
 * <p>Incluye observaciones (si existen) para roles de revisión (COORDINATOR, MASTER, ADMIN).
 */
@Schema(description = "Metadata de una evidencia fotografica de acta")
public record EvidenciaResponse(
    @Schema(description = "UUID publico de la evidencia") UUID id,
    @Schema(description = "ID de la JRV asociada") Long jrvId,
    @Schema(description = "ID del cargo asociado") Long cargoId,
    @Schema(description = "ID del usuario que subio la imagen") Long usuarioId,
    @Schema(description = "Clave en S3/MinIO del objeto", example = "evidencias/uuid.webp")
        String s3Key,
    @Schema(description = "Tamanio en bytes", example = "356200") long sizeBytes,
    @Schema(description = "MIME type del archivo", example = "image/webp") String contentType,
    @Schema(description = "Estado actual: EN_REVISION, APROBADA, RECHAZADA") EstadoEvidencia estado,
    @Schema(description = "Timestamp de creacion (UTC)") Instant creadoEn,
    @Schema(description = "Timestamp de ultima actualizacion (UTC)") Instant actualizadoEn,
    @Schema(description = "Observaciones marcadas sobre la evidencia") List<ObservacionDto> observaciones) {
  public static EvidenciaResponse from(Evidencia e) {
    return new EvidenciaResponse(
        e.getPublicId(),
        e.getJrvId(),
        e.getCargoId(),
        e.getUsuarioId(),
        e.getS3Key(),
        e.getSizeBytes(),
        e.getContentType(),
        e.getEstado(),
        e.getCreadoEn(),
        e.getActualizadoEn(),
        List.of()); // mapper sobrecargará este valor
  }
}
