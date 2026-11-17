package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;

/**
 * Datos para crear un usuario nuevo desde el panel de administracion. El email se genera solo.
 *
 * @param nombre nombre
 * @param apellido apellido
 * @param rolNombre rol a asignar
 * @param invitar si es true, la cuenta nace esperando la activacion y se manda la invitacion por
 *     email. Si es false (el valor por defecto), la cuenta nace activa con una contrasena temporal
 *     que el panel muestra, como siempre.
 */
@RegisterForReflection
public record CreateUsuarioRequest(
    @NotBlank String nombre,
    @NotBlank String apellido,
    @NotBlank String rolNombre,
    boolean invitar) {}
