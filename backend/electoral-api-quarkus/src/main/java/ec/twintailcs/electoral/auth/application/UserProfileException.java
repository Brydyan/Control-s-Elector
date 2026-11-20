package ec.twintailcs.electoral.auth.application;

/**
 * Excepción para errores en actualización de perfil de usuario.
 *
 * <p>Las excepciones lanzadas por el servicio se mapean a respuestas HTTP en el resource.
 */
public class UserProfileException extends RuntimeException {

  private final String errorCode;
  private final int statusCode;

  public UserProfileException(String message, String errorCode, int statusCode) {
    super(message);
    this.errorCode = errorCode;
    this.statusCode = statusCode;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public static UserProfileException passwordIncorrect() {
    return new UserProfileException("Contraseña incorrecta", "PASSWORD_INCORRECT", 400);
  }

  public static UserProfileException invalidEmailFormat() {
    return new UserProfileException("Formato de email inválido", "INVALID_EMAIL_FORMAT", 400);
  }

  public static UserProfileException emailTooLong() {
    return new UserProfileException("El email es demasiado largo", "EMAIL_TOO_LONG", 400);
  }

  public static UserProfileException emailSameAsControl() {
    return new UserProfileException(
        "El email personal no puede ser igual al email de control", "EMAIL_SAME_AS_CONTROL", 400);
  }

  public static UserProfileException emailAlreadyInUse() {
    return new UserProfileException(
        "Este email ya está en uso por otro usuario", "EMAIL_ALREADY_IN_USE", 400);
  }

  public static UserProfileException usuarioNoEncontrado() {
    return new UserProfileException("Usuario no encontrado", "USER_NOT_FOUND", 404);
  }
}
