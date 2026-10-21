package ec.twintailcs.electoral.acta.domain;

/**
 * Error de validacion del dominio de actas → 400 BAD REQUEST.
 *
 * <p>Equivalente del {@code ActaInvalidaException} del backend Spring: el {@code
 * GlobalExceptionHandler} de alla lo mapea a 400 con el mensaje del dominio (acta inexistente,
 * votos que no cuadran, JRV/cargo/candidato no encontrados, candidato que no pertenece al cargo).
 */
public class ActaInvalidaException extends RuntimeException {

  public ActaInvalidaException(String mensaje) {
    super(mensaje);
  }
}
