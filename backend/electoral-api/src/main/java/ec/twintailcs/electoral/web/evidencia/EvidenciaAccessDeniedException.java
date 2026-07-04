package ec.twintailcs.electoral.web.evidencia;

/**
 * 403. El rol del JWT no permite la operacion.
 * El controller la lanza despues de chequear rol/owner.
 */
public class EvidenciaAccessDeniedException extends RuntimeException {
    public EvidenciaAccessDeniedException(String msg) {
        super(msg);
    }
}
