package ec.twintailcs.electoral.acta.web.dto;

import ec.twintailcs.electoral.acta.domain.EstadoActa;
import java.time.Instant;

/** Respuesta publica de la API para un Acta. */
public record ActaResponseDto(
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
    Long totalVotosCandidatos) {}
