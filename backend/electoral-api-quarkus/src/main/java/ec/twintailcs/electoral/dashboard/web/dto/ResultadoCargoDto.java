package ec.twintailcs.electoral.dashboard.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * Bloque de resultados para un cargo, en {@code GET /api/v1/dashboard/resultados}.
 *
 * <p>Contiene los candidatos ordenados por votos descendente dentro de cada cargo. Las actas
 * ANULADAS no aportan votos: el filtro se aplica en la query de agregacion, nunca aqui.
 */
@RegisterForReflection
public record ResultadoCargoDto(
    Long cargoId, String cargoNombre, List<ResultadoCandidatoDto> candidatos) {}
