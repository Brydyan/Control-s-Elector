package ec.twintailcs.electoral.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body para POST /auth/register.
 *
 * <p>El email se genera automáticamente a partir de nombre, apellido y rol con el formato: {3
 * letras nombre}{3 letras apellido}{3 letras rol}@control.ttcs Ej: Juan Pérez, OBSERVER →
 * juaperobs@control.ttcs
 *
 * <p>El rol se pasa porque en esta version los registros los hace un MASTER desde el dashboard. El
 * endpoint no esta abierto a registro libre (sino seria un vector de spam). Quien llama debe tener
 * permisos elevados (se valida en el controller via @PreAuthorize).
 */
public record RegisterRequest(
    @NotBlank @Size(min = 2, max = 100) String nombre,
    @NotBlank @Size(min = 2, max = 100) String apellido,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Pattern(regexp = "OBSERVER|COORDINATOR|MASTER|ADMIN") String rol) {}
