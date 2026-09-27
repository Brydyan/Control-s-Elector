package ec.twintailcs.electoral.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Datos para crear un nuevo usuario. El email se genera automaticamente.")
public record CreateUsuarioRequest(
    @Schema(description = "Nombre del usuario", example = "Juan") @NotBlank String nombre,
    @Schema(description = "Apellido del usuario", example = "Perez") @NotBlank String apellido,
    @Schema(description = "Nombre del rol: MASTER, COORDINATOR, ADMIN u OBSERVER", example = "OBSERVER")
        @NotBlank
        String rolNombre) {}
