package ec.twintailcs.electoral.evidencia.domain;

/**
 * Estado de una evidencia subida al sistema.
 *
 * <p>- EN_REVISION: recien subida, esperando validacion humana. - APROBADA: validada por
 * COORDINATOR/MASTER. Cuenta para el resultado oficial. - RECHAZADA: imagen borrosa, mal
 * encuadrada, acta ilejible, etc. No cuenta.
 *
 * <p>Las transiciones validas son: EN_REVISION -> APROBADA (COORDINATOR/MASTER) EN_REVISION ->
 * RECHAZADA (COORDINATOR/MASTER) APROBADA -> EN_REVISION (master only, si necesita revalidar)
 */
public enum EstadoEvidencia {
  EN_REVISION,
  APROBADA,
  RECHAZADA
}
