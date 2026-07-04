package ec.twintailcs.electoral.auth.interfaces.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body para POST /auth/register.
 *
 * El rol se pasa porque en esta version los registros los hace un MASTER
 * desde el dashboard. El endpoint no esta abierto a registro libre
 * (sino seria un vector de spam). Quien llama debe tener permisos
 * elevados (se valida en el controller via @PreAuthorize).
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Pattern(regexp = "OBSERVER|COORDINATOR|MASTER|ADMIN") String rol
) {}
