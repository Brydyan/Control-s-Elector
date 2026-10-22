package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;

/** Datos para crear un usuario nuevo desde el panel de administracion. El email se genera solo. */
@RegisterForReflection
public record CreateUsuarioRequest(
    @NotBlank String nombre, @NotBlank String apellido, @NotBlank String rolNombre) {}
