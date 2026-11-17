package ec.twintailcs.electoral.shared.config;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * Parseo de duraciones en el formato corto que usa el backend Spring ({@code 15m}, {@code 7d},
 * {@code 30s}) y tambien ISO-8601 ({@code PT15M}).
 *
 * <p>Se parsea a mano y no con el binder de SmallRye para que el backend Spring y el backend
 * Quarkus acepten exactamente las mismas variables de entorno.
 */
public final class Durations {

  private Durations() {}

  public static Duration parse(String valor) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException("Duracion vacia");
    }
    String v = valor.trim();
    try {
      if (v.startsWith("P") || v.startsWith("p")) {
        return Duration.parse(v);
      }
      long cantidad = Long.parseLong(v.replaceAll("[^0-9]", ""));
      String unidad = v.replaceAll("[0-9]", "").toLowerCase();
      return switch (unidad) {
        case "ms" -> Duration.ofMillis(cantidad);
        case "s", "seg", "segundo", "segundos" -> Duration.ofSeconds(cantidad);
        case "m", "min", "minuto", "minutos" -> Duration.ofMinutes(cantidad);
        case "h", "hr", "hora", "horas" -> Duration.ofHours(cantidad);
        case "d", "dia", "dias" -> Duration.ofDays(cantidad);
        default -> throw new IllegalArgumentException("Unidad de duracion no reconocida: " + valor);
      };
    } catch (NumberFormatException | DateTimeParseException e) {
      throw new IllegalArgumentException("Duracion invalida: " + valor, e);
    }
  }
}
