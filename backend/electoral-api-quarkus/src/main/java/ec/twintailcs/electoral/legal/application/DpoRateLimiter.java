package ec.twintailcs.electoral.legal.application;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita las solicitudes al DPO por direccion IP.
 *
 * <p>El formulario del DPO es publico —lo puede usar cualquiera, no solo un usuario del sistema— y
 * sin limite se vuelve un canal para mandar spam o para inflar la tabla. Una solicitud cada 30
 * segundos por IP es suficiente para una persona y corta para un bot.
 *
 * <p>La cuenta es por ventana fija y vive en memoria del proceso, como el {@code
 * ActivationRateLimiter}: alcanza para frenar el abuso de un solo atacante y no necesita Redis. Las
 * entradas se limpian solas cuando la ventana vence, asi que un atacante que rota direcciones no
 * deja el mapa creciendo sin limite.
 *
 * <p>El limite del export de datos NO es de este tipo: ese es de 24 horas y sale de la tabla {@code
 * user_export_log}, porque tiene que sobrevivir a un reinicio del backend.
 */
@ApplicationScoped
public class DpoRateLimiter {

  /** Maximas solicitudes por IP dentro de la ventana. */
  public static final int MAX_SOLICITUDES = 1;

  /** Duracion de la ventana, en segundos. */
  public static final long VENTANA_SEGUNDOS = 30;

  private record Ventana(int solicitudes, long abiertaEn) {}

  private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

  /**
   * Cuenta una solicitud y lanza si la IP supero el tope.
   *
   * @param ip direccion del cliente
   * @throws ec.twintailcs.electoral.legal.domain.LegalException 429 si se supero el limite
   */
  public void registrar(String ip) {
    String clave = ip == null || ip.isBlank() ? "desconocida" : ip;
    long ahora = Instant.now().getEpochSecond();

    Ventana ventana =
        ventanas.compute(
            clave,
            (k, actual) -> {
              if (actual == null || ahora - actual.abiertaEn() >= VENTANA_SEGUNDOS) {
                return new Ventana(1, ahora);
              }
              return new Ventana(actual.solicitudes() + 1, actual.abiertaEn());
            });

    if (ventana.solicitudes() > MAX_SOLICITUDES) {
      long restantes = Math.max(1, VENTANA_SEGUNDOS - (ahora - ventana.abiertaEn()));
      throw ec.twintailcs.electoral.legal.domain.LegalException.limiteSuperado(
          "Max " + MAX_SOLICITUDES + " request per " + VENTANA_SEGUNDOS + " seconds.",
          (int) restantes);
    }
  }

  /** Solicitudes que lleva la IP en la ventana actual. */
  public int solicitudes(String ip) {
    String clave = ip == null || ip.isBlank() ? "desconocida" : ip;
    Ventana ventana = ventanas.get(clave);
    if (ventana == null) {
      return 0;
    }
    long ahora = Instant.now().getEpochSecond();
    if (ahora - ventana.abiertaEn() >= VENTANA_SEGUNDOS) {
      return 0;
    }
    return ventana.solicitudes();
  }
}
