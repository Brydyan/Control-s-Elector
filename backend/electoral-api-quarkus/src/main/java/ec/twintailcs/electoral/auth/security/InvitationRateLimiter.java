package ec.twintailcs.electoral.auth.security;

import ec.twintailcs.electoral.auth.domain.InvitacionException;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de invitaciones por direccion IP: una cada 5 segundos.
 *
 * <p>El endpoint de invitacion crea una cuenta y manda un email. Sin limite, un script puede zensar
 * cuentas y usar el sistema como una المركزية de spam: 5 segundos le dan al atacante 12 cuentas por
 * minuto, lo que es suficiente para inundar a un tercero con correos.
 *
 * <p>El limite es por IP y no por usuario a proposito: el caso que se quiere frenar es el envio
 * automatizado, y ese viene de una direccion, no de un MASTER. Un MASTER que invite a varias
 * personas seguidas choca con el limite, y por eso la respuesta dice exactamente cuanto falta.
 *
 * <p>La cuenta vive en memoria del proceso, como el {@code ActivationRateLimiter}. Alcanza para
 * frenar el abuso de un atacante y no necesita Redis. Lo que NO cubre es un atacante que rota IPs:
 * contra eso esta la autorizacion (solo MASTER) y la auditoria de cada invitacion.
 */
@ApplicationScoped
public class InvitationRateLimiter {

  /** Maximas invitaciones por IP dentro de la ventana. */
  public static final int MAX_INVITACIONES = 1;

  /** Duracion de la ventana, en segundos. */
  public static final long VENTANA_SEGUNDOS = 5;

  private record Ventana(int invitaciones, long abiertaEn) {}

  private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

  /**
   * Cuenta una invitacion y lanza si la IP supero el tope.
   *
   * @param ip direccion del cliente
   * @throws InvitacionException 429 si se supero el limite
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
              return new Ventana(actual.invitaciones() + 1, actual.abiertaEn());
            });

    if (ventana.invitaciones() > MAX_INVITACIONES) {
      long restantes = Math.max(1, VENTANA_SEGUNDOS - (ahora - ventana.abiertaEn()));
      throw InvitacionException.demasiadosIntentos(
          "Max " + MAX_INVITACIONES + " invitación por " + VENTANA_SEGUNDOS + " segundos.",
          (int) restantes);
    }
  }

  /** Invitaciones que lleva la IP en la ventana actual. */
  public int invitaciones(String ip) {
    String clave = ip == null || ip.isBlank() ? "desconocida" : ip;
    Ventana ventana = ventanas.get(clave);
    if (ventana == null) {
      return 0;
    }
    long ahora = Instant.now().getEpochSecond();
    if (ahora - ventana.abiertaEn() >= VENTANA_SEGUNDOS) {
      return 0;
    }
    return ventana.invitaciones();
  }
}
