package ec.twintailcs.electoral.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.UUID;

/**
 * Datos publicos de un usuario del sistema.
 *
 * <p>Mismo shape que el backend Spring: {@code passwordTemporal} solo aparece en la respuesta de la
 * creacion y se omite cuando es null.
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioResponseDto(
    UUID publicId,
    String email,
    String nombre,
    String apellido,
    String rolNombre,
    boolean habilitado,
    Instant creadoEn,
    String passwordTemporal) {

  public UsuarioResponseDto(
      UUID publicId,
      String email,
      String nombre,
      String apellido,
      String rolNombre,
      boolean habilitado,
      Instant creadoEn) {
    this(publicId, email, nombre, apellido, rolNombre, habilitado, creadoEn, null);
  }
}
