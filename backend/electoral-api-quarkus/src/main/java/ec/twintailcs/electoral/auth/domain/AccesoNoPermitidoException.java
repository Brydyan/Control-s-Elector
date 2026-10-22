package ec.twintailcs.electoral.auth.domain;

/**
 * El usuario esta autenticado pero no tiene el permiso que exige el endpoint.
 *
 * <p>Es el equivalente Quarkus del {@code AccessDeniedException} de Spring Security: lo lanza el
 * interceptor que evalua la anotacion {@link ec.twintailcs.electoral.auth.security.Requiere} y
 * mapea a 403 con el shape estandar de error.
 */
public class AccesoNoPermitidoException extends RuntimeException {

  public AccesoNoPermitidoException(String message) {
    super(message);
  }
}
