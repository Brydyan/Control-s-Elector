package ec.twintailcs.electoral.auth.application;

import java.util.List;

/**
 * Excepción para errores en cambio de contraseña.
 *
 * <p>Las excepciones lanzadas por el servicio se mapean a respuestas HTTP en el resource.
 */
public class PasswordChangeException extends RuntimeException {

  private final String errorCode;
  private final int statusCode;
  private final List<String> details;

  public PasswordChangeException(String message, String errorCode, int statusCode) {
    this(message, errorCode, statusCode, null);
  }

  public PasswordChangeException(
      String message, String errorCode, int statusCode, List<String> details) {
    super(message);
    this.errorCode = errorCode;
    this.statusCode = statusCode;
    this.details = details;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public List<String> getDetails() {
    return details;
  }

  public static PasswordChangeException currentPasswordIncorrect() {
    return new PasswordChangeException(
        "Contraseña actual incorrecta", "CURRENT_PASSWORD_INCORRECT", 400);
  }

  public static PasswordChangeException passwordMismatch() {
    return new PasswordChangeException(
        "Las nuevas contraseñas no coinciden", "PASSWORD_MISMATCH", 400);
  }

  public static PasswordChangeException passwordNotValid(List<String> missing) {
    return new PasswordChangeException(
        "La nueva contraseña no cumple los requisitos",
        "PASSWORD_NOT_VALID",
        400,
        missing);
  }

  public static PasswordChangeException passwordSameAsOld() {
    return new PasswordChangeException(
        "La nueva contraseña no puede ser igual a la anterior", "PASSWORD_SAME_AS_OLD", 400);
  }

  public static PasswordChangeException usuarioNoEncontrado() {
    return new PasswordChangeException("Usuario no encontrado", "USER_NOT_FOUND", 404);
  }

  public static PasswordChangeException rateLimitExceeded() {
    return new PasswordChangeException(
        "Demasiados intentos de cambio de contraseña. Intentá de nuevo en 1 hora",
        "RATE_LIMIT_EXCEEDED",
        429);
  }
}
