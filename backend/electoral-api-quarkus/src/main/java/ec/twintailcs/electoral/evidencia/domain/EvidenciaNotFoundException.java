package ec.twintailcs.electoral.evidencia.domain;

import java.util.UUID;

/**
 * Excepcion de dominio cuando no se encuentra la evidencia.
 *
 * <p>Se mapea a 404 NOT FOUND. El mensaje es el mismo que en el backend Spring: para la busqueda
 * por UUID publico dice «Evidencia no encontrada».
 */
public class EvidenciaNotFoundException extends RuntimeException {

  public EvidenciaNotFoundException(Long id) {
    super("Evidencia " + id + " not found");
  }

  public EvidenciaNotFoundException(UUID publicId) {
    super("Evidencia no encontrada");
  }
}
