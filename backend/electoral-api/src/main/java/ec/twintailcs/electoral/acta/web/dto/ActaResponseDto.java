package ec.twintailcs.electoral.acta.web.dto;

import ec.twintailcs.electoral.acta.domain.EstadoActa;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Respuesta publica de la API para un Acta. */
@Schema(description = "Respuesta publica de un acta electoral")
public record ActaResponseDto(
    @Schema(description = "ID interno del acta") Long id,
    @Schema(description = "ID de la JRV") Long jrvId,
    @Schema(description = "ID del cargo electoral") Long cargoId,
    @Schema(description = "ID del usuario que registro el acta") Long registradaPorId,
    @Schema(description = "Estado actual del acta") EstadoActa estado,
    @Schema(description = "Total de sufragantes en la JRV") Integer totalSufragantes,
    @Schema(description = "Total de votos en blanco") Integer votosBlancos,
    @Schema(description = "Total de votos nulos") Integer votosNulos,
    @Schema(description = "Fecha y hora de registro") Instant fechaRegistro,
    @Schema(description = "Motivo de anulacion (solo si estado=ANULADA)") String motivoAnulacion,
    @Schema(description = "Nombre del cargo electoral", example = "Alcalde/sa") String nombreCargo,
    @Schema(description = "Numero de la JRV", example = "0023") String numeroJrv,
    @Schema(description = "Suma de votos de todos los candidatos") Long totalVotosCandidatos) {}
