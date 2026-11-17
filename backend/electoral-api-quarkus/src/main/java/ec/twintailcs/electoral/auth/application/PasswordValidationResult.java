package ec.twintailcs.electoral.auth.application;

import java.util.List;

/**
 * Resultado de validar una contrasena contra los requisitos del sistema.
 *
 * <p>El resultado no es un booleano pelado: lleva <em>que</em> falta, con identificadores estables.
 * El frontend pinta una lista de requisitos en tiempo real y cada fila tiene que poder preguntar
 * "este requisito ya se cumplio?" sin parsear texto libre. Los identificadores son los mismos que
 * usa la API para devolver los faltantes, de modo que el mismo nombre sirve para el checklist y
 * para el mensaje de error.
 */
public record PasswordValidationResult(boolean valid, List<String> missing) {

  /** Longitud minima exigida. */
  public static final int MIN_LENGTH = 8;

  /** Requisito: al menos una mayuscula. */
  public static final String REQ_UPPERCASE = "uppercase";

  /** Requisito: al menos un numero. */
  public static final String REQ_NUMBER = "number";

  /** Requisito: al menos un caracter especial. */
  public static final String REQ_SPECIAL = "special_char";

  /** Requisito: la longitud minima. */
  public static final String REQ_LENGTH = "length";

  /**
   * Resultado valido.
   *
   * @param missing requisitos que faltan (vacio si la contrasena cumple todo)
   */
  public PasswordValidationResult {
    missing = List.copyOf(missing);
  }

  /** True si la contrasena cumple todos los requisitos. */
  public boolean isValid() {
    return valid;
  }

  /** Identificadores de los requisitos que faltan. */
  public List<String> getMissing() {
    return missing;
  }

  /** True si falta un requisito concreto. */
  public boolean falta(String requisito) {
    return missing.contains(requisito);
  }
}
