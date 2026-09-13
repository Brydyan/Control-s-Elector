package ec.twintailcs.electoral.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUsuarioRequest(
    @NotBlank String nombre,
    @NotBlank String apellido,
    @NotBlank String rolNombre,
    boolean habilitado) {}
