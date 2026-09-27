package ec.twintailcs.electoral.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciales para iniciar sesion")
public record LoginRequest(
    @Schema(description = "Email del usuario", example = "obs.juan.perez@control.ec")
        @NotBlank
        @Email
        String email,
    @Schema(description = "Password (min 8, max 100 caracteres)", example = "secreto123")
        @NotBlank
        @Size(min = 8, max = 100)
        String password) {}
