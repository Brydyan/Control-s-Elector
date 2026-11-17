package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Respuesta publica de la API para un Canton.
 *
 * <p>Mismo shape que el backend Spring: id interno, nombre y codigo INEC. El {@code provinciaId} se
 * suma para que la pantalla jerarquica pueda saber de que provincia cuelga el canton.
 */
@RegisterForReflection
public record CantonResponse(Long id, String nombre, String codigo, Long provinciaId) {}
