package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request para cambiar contraseña del usuario actual. */
@RegisterForReflection
public record ChangePasswordRequest(
    @NotBlank @Size(min = 8, max = 100) String currentPassword,
    @NotBlank @Size(min = 8, max = 100) String newPassword,
    @NotBlank @Size(min = 8, max = 100) String passwordConfirm) {}
