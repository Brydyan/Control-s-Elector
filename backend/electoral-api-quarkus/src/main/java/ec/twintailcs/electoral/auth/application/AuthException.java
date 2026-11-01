package ec.twintailcs.electoral.auth.application;

/** Error de autenticacion. Mapea a 401 con el shape estandar del backend. */
public class AuthException extends RuntimeException {
  public AuthException(String message) {
    super(message);
  }
}
