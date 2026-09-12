package ec.twintailcs.electoral.evidencia.domain;

/** Excepción de dominio cuando una evidencia no cumple las reglas de negocio o invariantes. */
public class EvidenciaInvalidaException extends RuntimeException {
  public EvidenciaInvalidaException(String message) {
    super(message);
  }
}
