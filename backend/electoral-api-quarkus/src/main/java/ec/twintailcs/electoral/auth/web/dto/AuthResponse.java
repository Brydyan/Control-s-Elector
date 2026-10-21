package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;

/**
 * Respuesta publica de login y refresh. Mismo shape que el backend Spring.
 *
 * <p>Los permisos NO viajan en el body: quedan en el claim {@code perms} del JWT.
 */
// En native image Jackson no encuentra un serializador para el record si no hay
// metadatos de reflexion: el resource devuelve `Response` (y no el DTO), asi que
// Quarkus no puede deducir el tipo a registrar. Sin esto, el login en nativo
// responde 500 con "No serializer found for class AuthResponse".
@RegisterForReflection
public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    String sesionId,
    UsuarioActual usuario) {

  /** Identificador minimo del usuario autenticado. */
  @RegisterForReflection
  public record UsuarioActual(UUID id, String email, String rol) {}
}
