package ec.twintailcs.electoral.acta.domain;

public class ActaInvalidaException extends RuntimeException {
  public ActaInvalidaException(String mensaje) {
    super(mensaje);
  }
}
