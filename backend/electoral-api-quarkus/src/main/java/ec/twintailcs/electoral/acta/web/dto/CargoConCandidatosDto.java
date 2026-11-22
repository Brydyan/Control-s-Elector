package ec.twintailcs.electoral.acta.web.dto;

import ec.twintailcs.electoral.electoral.web.dto.CandidatoResponse;
import ec.twintailcs.electoral.electoral.web.dto.PartidoResponse;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * Cargo de una JRV con sus candidatos y los partidos que compiten en la eleccion.
 *
 * <p>{@code partidos} se repite en cada cargo a proposito: el cliente arma el formulario cargo por
 * cargo y no tiene que resolver una tabla aparte.
 */
@RegisterForReflection
public record CargoConCandidatosDto(
    Long id, String nombre, List<CandidatoResponse> candidatos, List<PartidoResponse> partidos) {}
