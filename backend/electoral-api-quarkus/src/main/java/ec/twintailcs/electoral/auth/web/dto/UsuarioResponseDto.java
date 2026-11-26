package ec.twintailcs.electoral.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.UUID;

/**
 * Datos publicos de un usuario del sistema.
 *
 * <p>Mismo shape que el backend Spring: {@code passwordTemporal} solo aparece en la respuesta de la
 * creacion y se omite cuando es null.
 *
 * <p>{@code estadoUsuario} y {@code invitationSentAt} los necesita el panel de administracion para
 * distinguir a quien espera la activacion de a quien ya esta activo, y a quien ya le salio una
 * invitacion de a quien todavia no. Sin eso, la columna de casillas y la accion "re-enviar" no
 * pueden decidir nada.
 *
 * <p>{@code invitationStatus} (ODD #504, C) es el estado de la INVITACION, que no es lo mismo que
 * el de la cuenta: CREATED (nunca se le invito), INVITED (invitacion vigente), EXPIRED (vencio),
 * ACTIVE (la cuenta se activo) o DISABLED (la cuenta esta deshabilitada). Con {@code
 * invitationExpiresAt} el panel puede avisar cuando la invitacion esta por caducar.
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioResponseDto(
    UUID publicId,
    String email,
    String emailPersonal,
    String nombre,
    String apellido,
    String rolNombre,
    boolean habilitado,
    Instant creadoEn,
    String passwordTemporal,
    String estadoUsuario,
    Instant invitationSentAt,
    String invitationStatus,
    Instant invitationExpiresAt) {}
