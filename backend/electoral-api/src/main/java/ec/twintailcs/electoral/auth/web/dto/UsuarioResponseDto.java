package ec.twintailcs.electoral.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Datos publicos de un usuario del sistema")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioResponseDto(
    @Schema(description = "UUID publico del usuario (UUIDv7)") UUID publicId,
    @Schema(description = "Email institucional", example = "obs.juan.perez@control.ec")
        String email,
    @Schema(description = "Nombre del usuario", example = "Juan") String nombre,
    @Schema(description = "Apellido del usuario", example = "Perez") String apellido,
    @Schema(description = "Nombre del rol asignado", example = "OBSERVER") String rolNombre,
    @Schema(description = "true si la cuenta esta activa") boolean habilitado,
    @Schema(description = "Timestamp de creacion (UTC)") Instant creadoEn,
    @Schema(description = "Password temporal (solo presente en la respuesta de creacion)")
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
