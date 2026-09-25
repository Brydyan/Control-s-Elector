package ec.twintailcs.electoral.dashboard.web.dto;

/**
 * Línea de resultado por candidato dentro de un cargo (RF-DASH-04). Vienen ordenadas por {@code
 * votos} descendente desde la query JPQL; el service sólo las agrupa por cargo.
 */
public record ResultadoCandidatoDto(
    Long candidatoId, String candidatoNombre, String partidoNombre, Long votos) {}
