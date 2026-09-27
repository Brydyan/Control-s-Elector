package ec.twintailcs.electoral.acta.web.dto;

import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detalle completo de un acta para vistas de COORDINATOR y OBSERVER.
 *
 * <p>Extiende {@link ActaResponseDto} con colecciones que el listado plano no carga
 * (per tasks.md "qué NO hacer": no incluir colecciones en el listado para evitar N+1).
 */
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
