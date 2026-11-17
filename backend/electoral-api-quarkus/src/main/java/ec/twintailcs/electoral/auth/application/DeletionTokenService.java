package ec.twintailcs.electoral.auth.application;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Genera y valida tokens de recuperacion para cancelar eliminaciones de cuenta.
 *
 * <p>Los tokens siguen el mismo patron que los de invitacion: son base64url generados
 * aleatoriamente (48 bytes = 64 caracteres). Solo el HMAC se almacena en la base.
 */
@ApplicationScoped
public class DeletionTokenService {

  private final SecureRandom random = new SecureRandom();
  private static final int TOKEN_BYTE_LENGTH = 48; // 64 chars en base64url

  /**
   * Genera un token de recuperacion aleatorio para cancelar eliminacion.
   *
   * @return token en formato base64url (64 caracteres)
   */
  public String generarToken() {
    byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
