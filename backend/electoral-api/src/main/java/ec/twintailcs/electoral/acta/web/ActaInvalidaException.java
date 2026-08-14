package ec.twintailcs.electoral.acta.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ActaInvalidaException extends RuntimeException {
    public ActaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
