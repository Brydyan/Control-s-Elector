package ec.twintailcs.electoral.evidencia.domain;

/**
 * Datos minimos del acta que necesita el modulo de evidencias.
 *
 * <p>El modulo no mapea la entidad {@code acta} (pertenece al modulo de actas, que todavia no esta
 * portado): la lee por id con una consulta nativa y se queda solo con lo que necesita para validar
 * la subida.
 *
 * @param id id interno del acta
 * @param jrvId JRV del acta
 * @param cargoId cargo del acta
 * @param estado estado del acta (texto de la columna {@code estado})
 * @param registradaPor id interno del usuario que registro el acta
 */
public record ActaEvidencia(Long id, Long jrvId, Long cargoId, String estado, Long registradaPor) {

  /** Estado que impide volver a registrar evidencia sobre el acta. */
  public static final String ESTADO_ANULADA = "ANULADA";

  public boolean anulada() {
    return ESTADO_ANULADA.equals(estado);
  }
}
