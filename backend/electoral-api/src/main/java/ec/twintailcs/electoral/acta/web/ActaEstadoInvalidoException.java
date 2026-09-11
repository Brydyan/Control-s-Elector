package ec.twintailcs.electoral.acta.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ActaEstadoInvalidoException extends RuntimeException {
  public ActaEstadoInvalidoException(String mensaje) {
    super(mensaje);
  }
}
