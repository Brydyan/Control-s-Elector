package ec.twintailcs.electoral.auth.domain;

/** Excepción de dominio cuando las credenciales o la autenticación fallan. */
public class AuthException extends RuntimeException {
  public AuthException(String message) {
    super(message);
  }
}
