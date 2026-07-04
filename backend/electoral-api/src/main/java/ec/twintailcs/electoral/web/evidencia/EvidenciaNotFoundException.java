package ec.twintailcs.electoral.web.evidencia;

/**
 * 404. La evidencia no existe en la DB.
 */
public class EvidenciaNotFoundException extends RuntimeException {
    public EvidenciaNotFoundException(Long id) {
        super("Evidencia " + id + " not found");
    }
}
