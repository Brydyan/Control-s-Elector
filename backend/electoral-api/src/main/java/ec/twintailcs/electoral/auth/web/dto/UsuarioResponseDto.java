package ec.twintailcs.electoral.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

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
