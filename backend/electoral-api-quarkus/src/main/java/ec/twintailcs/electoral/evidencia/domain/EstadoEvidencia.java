package ec.twintailcs.electoral.evidencia.domain;

/**
 * Estado de una evidencia subida al sistema.
 *
 * <ul>
 *   <li>EN_REVISION: recien subida, esperando validacion humana.
 *   <li>APROBADA: validada por COORDINATOR/MASTER. Cuenta para el resultado oficial.
 *   <li>RECHAZADA: imagen borrosa, mal encuadrada, acta ilegible, etc. No cuenta.
 * </ul>
 *
 * <p>Se persiste como texto ({@code EnumType.STRING}), igual que en el backend Spring, para que el
 * valor de la columna sea legible y estable entre las dos implementaciones.
 */
public enum EstadoEvidencia {
  EN_REVISION,
  APROBADA,
  RECHAZADA
}
