package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;

/** Datos para actualizar un usuario existente. */
@RegisterForReflection
public record UpdateUsuarioRequest(
    @NotBlank String nombre,
    @NotBlank String apellido,
    @NotBlank String rolNombre,
    boolean habilitado) {}
