package ec.twintailcs.electoral.electoral.domain;

/**
 * 409. Violacion de una restriccion de negocio (ej. numeroLista duplicado).
 *
 * <p>Equivalente del {@code ConfiguracionConflictException} del backend Spring: el mapeo a 409 lo
 * hace {@code ConfiguracionConflictExceptionMapper}.
 */
public class ConfiguracionConflictException extends RuntimeException {

  public ConfiguracionConflictException(String mensaje) {
    super(mensaje);
  }
}
