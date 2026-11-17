package ec.twintailcs.electoral.electoral.domain;

/**
 * 404. Cargo, Partido o Candidato no existe.
 *
 * <p>Equivalente del {@code ConfiguracionNotFoundException} del backend Spring: el mapeo a 404 lo
 * hace {@code ConfiguracionNotFoundExceptionMapper}.
 */
public class ConfiguracionNotFoundException extends RuntimeException {

  public ConfiguracionNotFoundException(String mensaje) {
    super(mensaje);
  }
}
