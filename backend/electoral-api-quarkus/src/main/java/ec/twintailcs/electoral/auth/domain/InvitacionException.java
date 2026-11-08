package ec.twintailcs.electoral.auth.domain;

/**
 * Error del flujo de invitacion/activacion, con su status HTTP.
 *
 * <p>El flujo de activacion es publico (no exige sesion) y por eso necesita mas que el 401 de
 * {@code AuthException}: un token vencido es 400, una cuenta ya activada o un token ya consumido
 * son 409, y una contrasena que no cumple los requisitos es 400. Meter todo en el 401 de
 * autenticacion obligaria al frontend a adivinar la causa y a mostrar siempre "sesion expirada",
 * que es un mensaje falso para un link de invitacion vencido.
 */
public class InvitacionException extends RuntimeException {

  private final int status;
  private final int retryAfterSeconds;

  public InvitacionException(int status, String message) {
    this(status, message, 0);
  }

  public InvitacionException(int status, String message, int retryAfterSeconds) {
    super(message);
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  /** Status HTTP con el que se responde. */
  public int status() {
    return status;
  }

  /**
   * Segundos que faltan para poder reintentar. 0 si el error no es un limite de tasa.
   *
   * <p>El 429 del limite de invitaciones lo necesita: sin el, el cliente solo sabe que hay que
   * esperar "un rato" y no cuanto. Con el, puede avisar al operador exactamente cuando volver a
   * intentarlo.
   */
  public int retryAfterSeconds() {
    return retryAfterSeconds;
  }

  /** 400: el pedido esta mal formado o no cumple las reglas (token invalido, contrasena debil). */
  public static InvitacionException peticionInvalida(String mensaje) {
    return new InvitacionException(400, mensaje);
  }

  /** 409: el pedido es coherente pero choca con el estado actual (ya activado, token consumido). */
  public static InvitacionException conflicto(String mensaje) {
    return new InvitacionException(409, mensaje);
  }

  /** 429: se supero el limite de intentos. */
  public static InvitacionException demasiadosIntentos(String mensaje) {
    return new InvitacionException(429, mensaje);
  }

  /** 429 con el tiempo que falta para poder reintentar. */
  public static InvitacionException demasiadosIntentos(String mensaje, int retryAfterSeconds) {
    return new InvitacionException(429, mensaje, retryAfterSeconds);
  }
}
