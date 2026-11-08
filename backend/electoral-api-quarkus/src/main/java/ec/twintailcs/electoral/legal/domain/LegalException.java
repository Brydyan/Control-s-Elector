package ec.twintailcs.electoral.legal.domain;

/**
 * Error del flujo legal con su status HTTP y, cuando corresponde, el tiempo que falta para
 * reintentar.
 *
 * <p>Existe aparte de {@code InvitacionException} por dos razones:
 *
 * <ul>
 *   <li>El limite del export es de <b>24 horas</b>: el usuario necesita saber <i>cuando</i> puede
 *       volver a intentarlo, no solo que hay un error. El header {@code Retry-After} lleva ese
 *       dato.
 *   <li>Los mensajes del flujo legal son de cara al titular de los datos ("puede reintentar en X"),
 *       no al operador del sistema.
 * </ul>
 */
public class LegalException extends RuntimeException {

  private final int status;
  private final int retryAfterSeconds;

  public LegalException(int status, String message, int retryAfterSeconds) {
    super(message);
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public LegalException(int status, String message) {
    this(status, message, 0);
  }

  /** Status HTTP con el que se responde. */
  public int status() {
    return status;
  }

  /** Segundos que faltan para poder reintentar. 0 si el error no es un limite de tasa. */
  public int retryAfterSeconds() {
    return retryAfterSeconds;
  }

  /** 400: el pedido esta mal formado o no cumple las reglas. */
  public static LegalException peticionInvalida(String mensaje) {
    return new LegalException(400, mensaje);
  }

  /** 429: se supero el limite de tasa. */
  public static LegalException limiteSuperado(String mensaje, int retryAfterSeconds) {
    return new LegalException(429, mensaje, retryAfterSeconds);
  }
}
