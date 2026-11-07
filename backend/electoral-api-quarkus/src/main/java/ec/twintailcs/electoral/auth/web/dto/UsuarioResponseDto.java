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
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioResponseDto(
    UUID publicId,
    String email,
    String nombre,
    String apellido,
    String rolNombre,
    boolean habilitado,
    Instant creadoEn,
    String passwordTemporal,
    String estadoUsuario,
    Instant invitationSentAt) {}
