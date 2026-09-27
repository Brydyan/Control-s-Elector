package ec.twintailcs.electoral.dashboard.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Bloque de resultados para un cargo, en {@code GET /api/v1/dashboard/resultados}.
 *
 * <p>Contiene los candidatos ordenados por votos descendente dentro de cada cargo. RF-DASH-04 exige
 * que las actas ANULADAS no aporten votos: el filtro se aplica en la query de agregación ({@code
 * ActaDetalleRepository.votosTotalesPorCandidato()}), nunca aquí.
 */
@Schema(description = "Resultados electorales agrupados por cargo")
public record ResultadoCargoDto(
    @Schema(description = "ID del cargo electoral") Long cargoId,
    @Schema(description = "Nombre del cargo", example = "Prefecto/a Provincial") String cargoNombre,
    @Schema(description = "Candidatos con sus votos totales, ordenados desc")
        List<ResultadoCandidatoDto> candidatos) {}
