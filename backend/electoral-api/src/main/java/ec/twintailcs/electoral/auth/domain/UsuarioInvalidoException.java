package ec.twintailcs.electoral.auth.domain;

/** Excepción de dominio para violaciones de invariantes de Usuario. */
public class UsuarioInvalidoException extends RuntimeException {
  public UsuarioInvalidoException(String message) {
    super(message);
  }
}
