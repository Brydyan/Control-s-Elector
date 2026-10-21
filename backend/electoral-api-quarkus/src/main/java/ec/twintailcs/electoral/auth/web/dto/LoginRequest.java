package ec.twintailcs.electoral.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credenciales de inicio de sesion. Mismas validaciones que el backend Spring. */
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 100) String password) {}
