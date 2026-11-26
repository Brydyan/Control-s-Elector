package ec.twintailcs.electoral.storage;

/**
 * Excepción de alto nivel para operaciones de almacenamiento.
 *
 * <p>Captura errores de bajo nivel (S3Exception, IOException, conectividad, credenciales) y los
 * traduce a un contexto de dominio agnóstico del provider. Permite:
 * <ul>
 *   <li>Cambiar providers sin romper UseCase
 *   <li>Testear fallos de storage sin S3 real
 *   <li>Implementar retry/fallback policies coherentes
 * </ul>
 */
public class StorageException extends Exception {

  private final StorageErrorType errorType;

  public enum StorageErrorType {
    /** Credenciales inválidas, falta de permisos, endpoint incorrecto. */
    AUTHENTICATION_FAILED,
    /** Bucket/contenedor no existe o no es accesible. */
    RESOURCE_NOT_FOUND,
    /** Conexión rechazada, timeout, red no disponible. */
    CONNECTIVITY_ERROR,
    /** Espacio insuficiente, límite de API, etc. */
    QUOTA_EXCEEDED,
    /** Error no clasificado de bajo nivel. */
    UNKNOWN
  }

  public StorageException(String message) {
    this(message, StorageErrorType.UNKNOWN, null);
  }

  public StorageException(String message, StorageErrorType type) {
    this(message, type, null);
  }

  public StorageException(String message, Throwable cause) {
    this(message, StorageErrorType.UNKNOWN, cause);
  }

  public StorageException(String message, StorageErrorType type, Throwable cause) {
    super(message, cause);
    this.errorType = type;
  }

  public StorageErrorType getErrorType() {
    return errorType;
  }

  public boolean isRetryable() {
    return errorType == StorageErrorType.CONNECTIVITY_ERROR
        || errorType == StorageErrorType.QUOTA_EXCEEDED;
  }
}
