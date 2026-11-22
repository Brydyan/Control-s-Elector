package ec.twintailcs.electoral.acta.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * Catalogo completo de una JRV en una sola respuesta (T-PERF-OPT-01 B.2).
 *
 * <p>Reemplaza la secuencia de tres endpoints (cargos, candidatos por cargo y partidos) que el
 * formulario del observador pedia por separado: cinco cargos costaban once peticiones HTTP. Con
 * este endpoint es una sola, y los partidos viajan una vez por cargo para que el cliente no tenga
 * que cruzarlos.
 */
@RegisterForReflection
public record CargosConCandidatosResponse(List<CargoConCandidatosDto> cargos) {}
