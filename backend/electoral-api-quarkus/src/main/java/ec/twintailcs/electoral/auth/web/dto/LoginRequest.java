package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credenciales de inicio de sesion. Mismas validaciones que el backend Spring. */
// En native image Jackson necesita metadatos de reflexion para deserializar el
// record del body de la request.
@RegisterForReflection
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 100) String password) {}
