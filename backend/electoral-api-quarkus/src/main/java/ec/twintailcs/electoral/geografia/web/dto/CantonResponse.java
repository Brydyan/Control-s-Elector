package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Respuesta publica de la API para un Canton.
 *
 * <p>Mismo shape que el backend Spring: id interno, nombre y codigo INEC.
 */
@RegisterForReflection
public record CantonResponse(Long id, String nombre, String codigo) {}
