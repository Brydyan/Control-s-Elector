package ec.twintailcs.electoral.evidencia.web.dto;

import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta publica de la API. NO expone la columna {@code version} (interna) ni el SHA-256 crudo
 * por ahora; solo metadata util para el frontend.
 *
 * <p>Incluye las observaciones marcadas (si existen) para los roles de revision (COORDINATOR,
 * MASTER, ADMIN).
 */
@RegisterForReflection
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
    List<ObservacionDto> observaciones) {}
