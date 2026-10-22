package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.domain.EstadoActa;

/**
 * Conversion de los query params del modulo de actas.
 *
 * <p>El estado se recibe como texto y se convierte aca, y no como enum del framework, para
 * reproducir el error del backend Spring: un valor que no nombra un estado responde 400 con el
 * mensaje {@code invalid parameter format: estado}. Con el enum del framework, la conversion
 * fallida se resuelve en la capa HTTP y responde 404.
 */
public final class ParametrosActa {

  private ParametrosActa() {}

  /**
   * Convierte el parametro {@code estado}.
   *
   * @param valor texto del query param; {@code null} o vacio significa "sin filtro"
   * @return el estado, o {@code null} si no vino filtro
   * @throws IllegalArgumentException si el texto no nombra un estado valido
   */
  public static EstadoActa estado(String valor) {
    if (valor == null || valor.isBlank()) {
      return null;
    }
    try {
      return EstadoActa.valueOf(valor.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid parameter format: estado");
    }
  }
}
