package ec.twintailcs.electoral.electoral.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta publica de la API para un Candidato. */
@Schema(description = "Candidato inscrito en un cargo electoral")
public record CandidatoResponse(
    @Schema(description = "ID del candidato", example = "1") Long id,
    @Schema(description = "Nombre completo del candidato", example = "Maria Lopez") String nombre,
    @Schema(description = "Orden en la lista del partido", example = "1") Integer orden,
    @Schema(description = "ID del cargo al que se postula", example = "2") Long cargoId,
    @Schema(description = "ID del partido o movimiento", example = "3") Long partidoId) {}
