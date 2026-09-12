package ec.twintailcs.electoral.acta.domain;

public class ActaEstadoInvalidoException extends RuntimeException {
  public ActaEstadoInvalidoException(String mensaje) {
    super(mensaje);
  }
}
