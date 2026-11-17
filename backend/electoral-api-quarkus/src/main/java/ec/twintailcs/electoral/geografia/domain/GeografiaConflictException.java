package ec.twintailcs.electoral.geografia.domain;

import java.util.Map;

/**
 * 409. Conflicto al crear, editar o eliminar geografia electoral.
 *
 * <p>Casos: codigo de canton repetido, nombre de parroquia repetido en el canton, numero de JRV
 * repetido, y borrado de una entidad que todavia tiene hijos (la eliminacion en cascada esta
 * prohibida por decision de diseno: la geografia es data electoral critica).
 *
 * <p>{@code campos} lleva los datos extra del conflicto (por ejemplo cuantos hijos tiene el canton)
 * en el bloque {@code fields} de la respuesta de error, que es donde el contrato estandar los
 * espera.
 */
public class GeografiaConflictException extends RuntimeException {

  private final Map<String, String> campos;

  public GeografiaConflictException(String mensaje) {
    this(mensaje, Map.of());
  }

  public GeografiaConflictException(String mensaje, Map<String, String> campos) {
    super(mensaje);
    this.campos = campos == null ? Map.of() : campos;
  }

  /** Datos extra del conflicto, para el bloque {@code fields} de la respuesta. */
  public Map<String, String> getCampos() {
    return campos;
  }
}
