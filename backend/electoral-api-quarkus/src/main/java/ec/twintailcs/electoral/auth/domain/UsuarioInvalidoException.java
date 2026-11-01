package ec.twintailcs.electoral.auth.domain;

/**
 * Excepcion de dominio para violaciones de invariantes de {@link Usuario}.
 *
 * <p>Port exacto del backend Spring: mapea a 400 con el shape estandar de error.
 */
public class UsuarioInvalidoException extends RuntimeException {

  public UsuarioInvalidoException(String message) {
    super(message);
  }
}
