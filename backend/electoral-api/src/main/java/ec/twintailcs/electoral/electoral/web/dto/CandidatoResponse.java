package ec.twintailcs.electoral.electoral.web.dto;

/** Respuesta publica de la API para un Candidato. */
public record CandidatoResponse(
    Long id, String nombre, Integer orden, Long cargoId, Long partidoId) {}
