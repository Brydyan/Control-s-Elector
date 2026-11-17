package ec.twintailcs.electoral.acta.web.dto;

import ec.twintailcs.electoral.acta.domain.EstadoActa;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detalle completo de un acta para vistas de COORDINATOR y OBSERVER.
 *
 * <p>Extiende el listado plano con las colecciones que ese no carga (votos por candidato, evidencia
 * y motivo de disputa), igual que el {@code ActaDetalleCompletoDto} del backend Spring.
 */
@RegisterForReflection
public record ActaDetalleCompletoDto(
    // campos base heredados de ActaResponseDto
    Long id,
    Long jrvId,
    Long cargoId,
    Long registradaPorId,
    EstadoActa estado,
    Integer totalSufragantes,
    Integer votosBlancos,
    Integer votosNulos,
    Instant fechaRegistro,
    String motivoAnulacion,
    String nombreCargo,
    String numeroJrv,
    Long totalVotosCandidatos,
    // campos adicionales del detalle completo
    List<ActaDetalleDto> detalles,
    UUID evidenciaPublicId,
    String urlEvidencia,
    String motivoDisputa) {}
