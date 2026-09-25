package ec.twintailcs.electoral.evidencia.web.dto;

import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta pública de la API. NO expone la columna {@code version} (interna) ni el SHA-256 crudo
 * por ahora; solo metadata util para el FE.
 *
 * <p>Incluye observaciones (si existen) para roles de revisión (COORDINATOR, MASTER, ADMIN).
 */
public record EvidenciaResponse(
    UUID id,
    Long jrvId,
    Long cargoId,
    Long usuarioId,
    String s3Key,
    long sizeBytes,
    String contentType,
    EstadoEvidencia estado,
    Instant creadoEn,
    Instant actualizadoEn,
    List<ObservacionDto> observaciones) {
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
        List.of());  // mapper sobrecargará este valor
  }
}
