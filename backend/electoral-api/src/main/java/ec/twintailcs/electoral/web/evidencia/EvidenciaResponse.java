package ec.twintailcs.electoral.web.evidencia;

import ec.twintailcs.electoral.domain.evidencia.EstadoEvidencia;
import ec.twintailcs.electoral.domain.evidencia.Evidencia;
import java.time.Instant;

/**
 * Respuesta publica de la API. NO expone la columna {@code version}
 * (interna) ni el SHA-256 crudo por ahora; solo metadata util para el FE.
 */
public record EvidenciaResponse(
        Long id,
        Long jrvId,
        Long cargoId,
        Long usuarioId,
        String s3Key,
        long sizeBytes,
        String contentType,
        EstadoEvidencia estado,
        Instant creadoEn,
        Instant actualizadoEn
) {
    public static EvidenciaResponse from(Evidencia e) {
        return new EvidenciaResponse(
                e.getId(), e.getJrvId(), e.getCargoId(), e.getUsuarioId(),
                e.getS3Key(), e.getSizeBytes(), e.getContentType(),
                e.getEstado(), e.getCreadoEn(), e.getActualizadoEn());
    }
}
