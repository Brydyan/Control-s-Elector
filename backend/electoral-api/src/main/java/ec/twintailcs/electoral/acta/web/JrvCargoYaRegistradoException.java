package ec.twintailcs.electoral.acta.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class JrvCargoYaRegistradoException extends RuntimeException {
  public JrvCargoYaRegistradoException(Long jrvId, Long cargoId) {
    super("Ya existe un acta activa para JRV " + jrvId + " y cargo " + cargoId);
  }
}
