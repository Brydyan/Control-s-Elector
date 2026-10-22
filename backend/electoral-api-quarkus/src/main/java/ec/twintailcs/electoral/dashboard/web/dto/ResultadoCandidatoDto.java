package ec.twintailcs.electoral.dashboard.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Linea de resultado por candidato dentro de un cargo. Vienen ordenadas por {@code votos}
 * descendente desde la query de agregacion; el service solo las agrupa por cargo.
 */
@RegisterForReflection
public record ResultadoCandidatoDto(
    Long candidatoId, String candidatoNombre, String partidoNombre, Long votos) {}
