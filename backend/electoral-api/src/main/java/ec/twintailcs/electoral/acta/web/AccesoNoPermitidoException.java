package ec.twintailcs.electoral.acta.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccesoNoPermitidoException extends RuntimeException {
  public AccesoNoPermitidoException(String mensaje) {
    super(mensaje);
  }
}
