package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request para actualizar el email personal del usuario. */
@RegisterForReflection
public record UpdatePersonalEmailRequest(
    @Email String emailPersonal, // puede ser null para limpiar el email
    @NotBlank @Size(min = 8, max = 100) String password) {}
