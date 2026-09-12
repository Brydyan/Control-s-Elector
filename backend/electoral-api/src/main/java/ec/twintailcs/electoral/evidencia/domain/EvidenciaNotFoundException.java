package ec.twintailcs.electoral.evidencia.domain;

import java.util.UUID;

/** Excepción de dominio cuando no se encuentra la evidencia. */
public class EvidenciaNotFoundException extends RuntimeException {
  public EvidenciaNotFoundException(Long id) {
    super("Evidencia " + id + " not found");
  }

  public EvidenciaNotFoundException(UUID publicId) {
    super("Evidencia no encontrada");
  }
}
