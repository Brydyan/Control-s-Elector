package ec.twintailcs.electoral.storage.s3;

import ec.twintailcs.electoral.storage.S3Provider;
import ec.twintailcs.electoral.storage.StorageException;
import ec.twintailcs.electoral.storage.StorageException.StorageErrorType;
import ec.twintailcs.electoral.storage.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Adaptador de {@link S3StorageService} a la interfaz {@link StorageService}.
 *
 * <p>Captura excepciones de bajo nivel (S3Exception) y las traduce a StorageException con contexto
 * de error. Permite que UseCase sea agnóstico del provider.
 *
 * <p><b>Responsabilidades:</b>
 * <ul>
 *   <li>Traducir S3Exception → StorageException con clasificación de error
 *   <li>Loguear errores con contexto completo
 *   <li>Validar salud del storage (credenciales, bucket, etc)
 * </ul>
 *
 * <p><b>CDI:</b> @S3Provider qualifier para distinguir esta implementación. Lo inyecta
 * FallbackStorageService como primary. StorageConfiguration lo produce como singleton default.
 */
@ApplicationScoped
@S3Provider
public class S3StorageAdapter implements StorageService {

  private static final Logger log = Logger.getLogger(S3StorageAdapter.class);

  @Inject S3StorageService s3Service;
  @Inject S3Properties props;

  @Override
  public void upload(String key, InputStream input, long size, String mimeType)
      throws StorageException, IOException {
    try {
      s3Service.upload(key, input, size, mimeType);
    } catch (S3Exception e) {
      StorageErrorType type = classifyS3Error(e);
      log.errorf(
          e,
          "S3 upload failed [%s]: key=%s, bucket=%s, status=%d, error=%s",
          type,
          key,
          props.getBucket(),
          e.statusCode(),
          e.awsErrorDetails());
      throw new StorageException(
          String.format(
              "S3 upload failed [%s]: %s",
              type, e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage()),
          type,
          e);
    }
  }

  @Override
  public void delete(String key) throws StorageException {
    try {
      s3Service.delete(key);
    } catch (S3Exception e) {
      StorageErrorType type = classifyS3Error(e);
      log.errorf(
          e,
          "S3 delete failed [%s]: key=%s, bucket=%s, status=%d",
          type,
          key,
          props.getBucket(),
          e.statusCode());
      throw new StorageException(
          String.format("S3 delete failed [%s]: %s", type, e.getMessage()),
          type,
          e);
    }
  }

  @Override
  public InputStream download(String key) throws StorageException {
    try {
      return s3Service.download(key);
    } catch (S3Exception e) {
      StorageErrorType type = classifyS3Error(e);
      log.errorf(
          e,
          "S3 download failed [%s]: key=%s, bucket=%s, status=%d",
          type,
          key,
          props.getBucket(),
          e.statusCode());
      throw new StorageException(
          String.format("S3 download failed [%s]: %s", type, e.getMessage()),
          type,
          e);
    }
  }

  @Override
  public String presignDownloadUrl(String key) throws StorageException {
    try {
      return s3Service.presignDownloadUrl(key);
    } catch (S3Exception e) {
      StorageErrorType type = classifyS3Error(e);
      log.errorf(
          e,
          "S3 presign download failed [%s]: key=%s, bucket=%s, status=%d",
          type,
          key,
          props.getBucket(),
          e.statusCode());
      throw new StorageException(
          String.format("S3 presign download failed [%s]: %s", type, e.getMessage()),
          type,
          e);
    }
  }

  @Override
  public String presignUploadUrl(String key) throws StorageException {
    try {
      return s3Service.presignUploadUrl(key);
    } catch (S3Exception e) {
      StorageErrorType type = classifyS3Error(e);
      log.errorf(
          e,
          "S3 presign upload failed [%s]: key=%s, bucket=%s, status=%d",
          type,
          key,
          props.getBucket(),
          e.statusCode());
      throw new StorageException(
          String.format("S3 presign upload failed [%s]: %s", type, e.getMessage()),
          type,
          e);
    }
  }

  @Override
  public boolean isHealthy() {
    try {
      // Intenta generar URL prefirmada vacía como health check
      // Si falla aquí, es problema de credenciales/bucket
      s3Service.presignUploadUrl("_health_check_" + System.currentTimeMillis());
      return true;
    } catch (S3Exception e) {
      log.warnf(
          "S3 health check failed [%s]: status=%d",
          classifyS3Error(e), e.statusCode());
      return false;
    }
  }

  /**
   * Clasifica un S3Exception a su StorageErrorType.
   *
   * <p>Mapeo:
   * <ul>
   *   <li>401, 403 → AUTHENTICATION_FAILED (credenciales inválidas)
   *   <li>404 → RESOURCE_NOT_FOUND (bucket no existe)
   *   <li>500, 503, timeout → CONNECTIVITY_ERROR (backend S3 caído)
   *   <li>429 → QUOTA_EXCEEDED (rate limit o espacio insuficiente)
   *   <li>Otros → UNKNOWN
   * </ul>
   */
  private StorageErrorType classifyS3Error(S3Exception e) {
    int status = e.statusCode();
    if (status == 401 || status == 403) {
      return StorageErrorType.AUTHENTICATION_FAILED;
    }
    if (status == 404) {
      return StorageErrorType.RESOURCE_NOT_FOUND;
    }
    if (status == 429) {
      return StorageErrorType.QUOTA_EXCEEDED;
    }
    if (status >= 500 || e.getMessage().contains("timeout") || e.getMessage().contains("refused")) {
      return StorageErrorType.CONNECTIVITY_ERROR;
    }
    return StorageErrorType.UNKNOWN;
  }
}
