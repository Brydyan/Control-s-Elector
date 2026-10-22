package ec.twintailcs.electoral.acta.domain;

/**
 * Ya existe un acta activa para el par (JRV, cargo) → 409 CONFLICT.
 *
 * <p>El indice parcial {@code uk_acta_activa} sobre {@code acta(jrv_id, cargo_id)} permite una
 * sola acta activa por par. Cuando dos observers registran el mismo par a la vez, el segundo choca
 * con el indice: esa colision se traduce a esta excepcion, no a un error de servidor.
 */
public class JrvCargoYaRegistradoException extends RuntimeException {

  public JrvCargoYaRegistradoException(Long jrvId, Long cargoId) {
    super("Ya existe un acta activa para JRV " + jrvId + " y cargo " + cargoId);
  }
}
