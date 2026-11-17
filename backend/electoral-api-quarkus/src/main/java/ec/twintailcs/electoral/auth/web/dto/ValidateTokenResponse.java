package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

/**
 * Respuesta de la validacion de un token de activacion.
 *
 * <p>No dice si el usuario existe. Decirlo permitiria enumerar cuentas: un atacante que pruebe
 * tokens al azar podria distinguir "token valido de alguien que existe" de "token valido de un id
 * que no existe". Lo que el frontend necesita saber es si puede mostrar el formulario.
 *
 * <p>El {@code username} es la excepcion, y es segura: solo se devuelve cuando el token YA es
 * valido. Quien tiene el token es esa persona, asi que mostrarle su propio login no revela nada que
 * no pueda ver de su correo de invitacion. Con un token invalido la respuesta no dice nada.
 *
 * @param valid true si el token se puede usar para activar
 * @param usuarioId id publico del usuario, cuando el token es valido
 * @param expiresAt momento en que el token expira, cuando es valido
 * @param username login del usuario (el correo de Control con el que entra), cuando el token es
 *     valido
 */
@RegisterForReflection
public record ValidateTokenResponse(
    boolean valid, String usuarioId, Instant expiresAt, String username) {

  /** Respuesta para un token que no se puede usar. */
  public static ValidateTokenResponse invalido() {
    return new ValidateTokenResponse(false, null, null, null);
  }
}
