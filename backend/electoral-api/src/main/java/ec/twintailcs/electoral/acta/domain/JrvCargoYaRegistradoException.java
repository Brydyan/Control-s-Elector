package ec.twintailcs.electoral.acta.domain;

public class JrvCargoYaRegistradoException extends RuntimeException {
  public JrvCargoYaRegistradoException(Long jrvId, Long cargoId) {
    super("Ya existe un acta activa para JRV " + jrvId + " y cargo " + cargoId);
  }
}
