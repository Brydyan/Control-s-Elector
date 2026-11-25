package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

/**
 * Respuesta de la API para un Recinto.
 *
 * <p>Incluye metadata para el panel de administración: nombre del coordinador, parroquia, etc.
 */
@RegisterForReflection
public record RecintoResponse(
    Long id,
    String nombre,
    Long parroquiaId,
    String parroquia,
    CoordinadorInfo coordinador,
    Integer totalMesas,
    String estado,
    Instant fechaCreacion) {

  @RegisterForReflection
  public record CoordinadorInfo(Long id, String nombre, String email) {}
}
