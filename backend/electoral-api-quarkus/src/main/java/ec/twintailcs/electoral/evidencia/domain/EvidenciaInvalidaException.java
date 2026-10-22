package ec.twintailcs.electoral.evidencia.domain;

/**
 * Excepcion de dominio cuando una evidencia no cumple las reglas de negocio o las invariantes.
 *
 * <p>Se mapea a 400 BAD REQUEST: es una entrada invalida (archivo que no es una imagen, acta que
 * no existe, estado que no es transicionable, demasiadas observaciones), no un fallo del servidor.
 */
public class EvidenciaInvalidaException extends RuntimeException {

  public EvidenciaInvalidaException(String message) {
    super(message);
  }
}
