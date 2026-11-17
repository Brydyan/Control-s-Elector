package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Respuesta publica de la API para una Provincia. */
@RegisterForReflection
public record ProvinciaResponse(Long id, String nombre, String codigo) {}
