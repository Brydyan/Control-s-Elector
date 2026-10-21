package ec.twintailcs.electoral.acta.domain;

/**
 * Transicion de estado invalida → 409 CONFLICT.
 *
 * <p>Equivalente del {@code ActaEstadoInvalidoException} del backend Spring: lo dispara la maquina
 * de estados cuando la transicion pedida no existe desde el estado actual (disputar un acta que ya
 * esta en disputa, aprobar un acta que no esta ENVIADA ni CORREGIDA, anular un acta que no esta
 * APROBADA).
 */
public class ActaEstadoInvalidoException extends RuntimeException {

  public ActaEstadoInvalidoException(String mensaje) {
    super(mensaje);
  }
}
