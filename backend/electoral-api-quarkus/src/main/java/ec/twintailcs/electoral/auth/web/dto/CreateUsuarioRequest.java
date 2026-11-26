package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;

/**
 * Datos para crear un usuario nuevo desde el panel de administracion. El email de control se
 * genera solo.
 *
 * @param nombre nombre
 * @param apellido apellido
 * @param rolNombre rol a asignar
 * @param emailPersonal email personal del usuario (correo de contacto, NO el email de control)
 * @param invitar si es true, la cuenta nace esperando la activacion y se manda la invitacion por
 *     email. Si es false (el valor por defecto), la cuenta nace activa con una contrasena temporal
 *     que el panel muestra, como siempre.
 * @param sendInvitation si es true, determina que la cuenta se cree con estado PENDING_INVITATION e
 *     invitacion por email. Si es false, la cuenta se crea ACTIVA sin invitacion. Reemplaza a
 *     {@code invitar} en la logica ODD, pero ambos se aceptan por compatibilidad.
 */
@RegisterForReflection
public record CreateUsuarioRequest(
    @NotBlank String nombre,
    @NotBlank String apellido,
    @NotBlank String rolNombre,
    String emailPersonal,
    boolean invitar,
    boolean sendInvitation) {}
