package ec.twintailcs.electoral.auth.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los intentos de activacion por direccion IP.
 *
 * <p>El endpoint de activacion es publico —no exige sesion— y acepta un token que se puede adivinar
 * por fuerza bruta si el token es corto o elalgoritmo es debil. El limite acota el trabajo que un
 * atacante puede pedir al servidor: sin el, el endpoint es un oracle de adivinacion de tokens.
 *
 * <p>La cuenta es por ventana fija: al superar el tope dentro de la ventana, la siguiente peticion
 * devuelve 429 hasta que la ventana se reabre. Es memoria del proceso, no un almacen compartido:
 * alcanza para frenar el abuso de un solo atacante y no necesita Redis.
 *
 * <p>Las entradas se limpian solas cuando la ventana vence, asi que un atacante que rotura
 * direcciones IP no deja el mapa creciendo sin limite.
 */
@ApplicationScoped
public class ActivationRateLimiter {

  /** Maximos intentos por IP dentro de la ventana. */
  public static final int MAX_INTENTOS = 10;

  /** Duracion de la ventana, en segundos. */
  public static final long VENTANA_SEGUNDOS = 60;

  private record Ventana(int intentos, long abiertaEn) {}

  private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

  /**
   * Cuenta un intento y lanza si la IP supero el tope.
   *
   * @param ip direccion del cliente
   * @throws ec.twintailcs.electoral.auth.domain.InvitacionException 429 si se supero el limite
   */
  public void registrarIntento(String ip) {
    String clave = ip == null ? "desconocida" : ip;
    long ahora = Instant.now().getEpochSecond();

    Ventana ventana =
        ventanas.compute(
            clave,
            (k, actual) -> {
              if (actual == null || ahora - actual.abiertaEn() >= VENTANA_SEGUNDOS) {
                return new Ventana(1, ahora);
              }
              return new Ventana(actual.intentos() + 1, actual.abiertaEn());
            });

    if (ventana.intentos() > MAX_INTENTOS) {
      throw ec.twintailcs.electoral.auth.domain.InvitacionException.demasiadosIntentos(
          "Demasiados intentos de activación. Esperá un minuto e intentá de nuevo.");
    }
  }

  /** Intentos que lleva la IP en la ventana actual. */
  public int intentos(String ip) {
    Ventana ventana = ventanas.get(ip == null ? "desconocida" : ip);
    if (ventana == null) {
      return 0;
    }
    long ahora = Instant.now().getEpochSecond();
    if (ahora - ventana.abiertaEn() >= VENTANA_SEGUNDOS) {
      return 0;
    }
    return ventana.intentos();
  }
}
