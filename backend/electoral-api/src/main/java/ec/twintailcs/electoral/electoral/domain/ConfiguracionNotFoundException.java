package ec.twintailcs.electoral.electoral.domain;

/** 404. Cargo, Partido o Candidato no existe. */
public class ConfiguracionNotFoundException extends RuntimeException {
  public ConfiguracionNotFoundException(String mensaje) {
    super(mensaje);
  }
}
