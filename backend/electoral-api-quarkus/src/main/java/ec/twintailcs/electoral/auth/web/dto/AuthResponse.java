package ec.twintailcs.electoral.auth.web.dto;

import java.util.UUID;

/**
 * Respuesta publica de login y refresh. Mismo shape que el backend Spring.
 *
 * <p>Los permisos NO viajan en el body: quedan en el claim {@code perms} del JWT.
 */
public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    String sesionId,
    UsuarioActual usuario) {

  /** Identificador minimo del usuario autenticado. */
  public record UsuarioActual(UUID id, String email, String rol) {}
}
