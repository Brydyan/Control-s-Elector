package ec.twintailcs.electoral.acta.domain;

public class AccesoNoPermitidoException extends RuntimeException {
  public AccesoNoPermitidoException(String mensaje) {
    super(mensaje);
  }
}
