package ec.twintailcs.electoral.electoral.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 404. Cargo, Partido o Candidato no existe. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ConfiguracionNotFoundException extends RuntimeException {
  public ConfiguracionNotFoundException(String mensaje) {
    super(mensaje);
  }
}
