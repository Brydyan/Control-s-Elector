package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Respuesta publica de la API para una Parroquia. */
@RegisterForReflection
public record ParroquiaResponse(Long id, String nombre, boolean esUrbana, Long cantonId) {}
