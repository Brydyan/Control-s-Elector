package ec.twintailcs.electoral.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Datos para actualizar un usuario existente")
public record UpdateUsuarioRequest(
    @Schema(description = "Nombre del usuario", example = "Juan") @NotBlank String nombre,
    @Schema(description = "Apellido del usuario", example = "Perez") @NotBlank String apellido,
    @Schema(description = "Nombre del rol: MASTER, COORDINATOR, ADMIN u OBSERVER", example = "COORDINATOR")
        @NotBlank
        String rolNombre,
    @Schema(description = "true = habilitado, false = deshabilitado", example = "true")
        boolean habilitado) {}
