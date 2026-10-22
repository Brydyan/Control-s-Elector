package ec.twintailcs.electoral.electoral.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Respuesta publica de la API para un Candidato. */
@RegisterForReflection
public record CandidatoResponse(
    Long id, String nombre, Integer orden, Long cargoId, Long partidoId) {}
