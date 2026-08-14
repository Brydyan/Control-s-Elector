package ec.twintailcs.electoral.electoral.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 409. Violacion de una restriccion de negocio (ej. numeroLista duplicado).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConfiguracionConflictException extends RuntimeException {
    public ConfiguracionConflictException(String mensaje) {
        super(mensaje);
    }
}
