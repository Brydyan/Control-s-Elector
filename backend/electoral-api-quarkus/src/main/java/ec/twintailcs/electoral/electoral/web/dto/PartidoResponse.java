package ec.twintailcs.electoral.electoral.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Respuesta publica de la API para un Partido. */
@RegisterForReflection
public record PartidoResponse(Long id, String nombre, String siglas, Integer numeroLista) {}
