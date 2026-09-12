package ec.twintailcs.electoral.electoral.domain;

/** 409. Violacion de una restriccion de negocio (ej. numeroLista duplicado). */
public class ConfiguracionConflictException extends RuntimeException {
  public ConfiguracionConflictException(String mensaje) {
    super(mensaje);
  }
}
