package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Respuesta publica de la API para una JRV. */
@RegisterForReflection
public record JrvResponse(
    Long id, String numero, String recinto, int totalElectores, Long parroquiaId) {}
