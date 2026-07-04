package ec.twintailcs.electoral.evidencia.web.dto;

import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import java.time.Instant;

/**
 * Respuesta publica de la API. NO expone la columna {@code version}
 * (interna) ni el SHA-256 crudo por ahora; solo metadata util para el FE.
 */
public record EvidenciaResponseDTO(
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
    public static EvidenciaResponseDTO from(Evidencia e) {
        return new EvidenciaResponseDTO(
                e.getId(), e.getJrvId(), e.getCargoId(), e.getUsuarioId(),
                e.getS3Key(), e.getSizeBytes(), e.getContentType(),
                e.getEstado(), e.getCreadoEn(), e.getActualizadoEn());
    }
}
