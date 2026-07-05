package ec.twintailcs.electoral.evidencia.web;

import java.util.UUID;

/**
 * 404. La evidencia no existe en la DB.
 */
public class EvidenciaNotFoundException extends RuntimeException {
    public EvidenciaNotFoundException(Long id) {
        super("Evidencia " + id + " not found");
    }

    public EvidenciaNotFoundException(UUID publicId) {
        super("Evidencia no encontrada");
    }
}
