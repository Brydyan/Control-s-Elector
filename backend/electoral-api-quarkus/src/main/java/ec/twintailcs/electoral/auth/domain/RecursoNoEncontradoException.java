package ec.twintailcs.electoral.auth.domain;

/**
 * El recurso pedido no existe (o ya no esta disponible).
 *
 * <p>Equivalente del {@code ResponseStatusException} con 404 del backend Spring: mapea a 404 con
 * el shape estandar de error y con el mensaje que le pase el llamador.
 */
public class RecursoNoEncontradoException extends RuntimeException {

  public RecursoNoEncontradoException(String message) {
    super(message);
  }
}
