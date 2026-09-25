package ec.twintailcs.electoral.dashboard.web.dto;

import java.util.List;

/**
 * Bloque de resultados para un cargo, en {@code GET /api/v1/dashboard/resultados}.
 *
 * <p>Contiene los candidatos ordenados por votos descendente dentro de cada cargo. RF-DASH-04 exige
 * que las actas ANULADAS no aporten votos: el filtro se aplica en la query de agregación ({@code
 * ActaDetalleRepository.votosTotalesPorCandidato()}), nunca aquí.
 */
public record ResultadoCargoDto(
    Long cargoId, String cargoNombre, List<ResultadoCandidatoDto> candidatos) {}
