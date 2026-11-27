package ec.twintailcs.electoral.storage.fallback;

import ec.twintailcs.electoral.storage.FallbackProvider;
import ec.twintailcs.electoral.storage.FallbackService;
import ec.twintailcs.electoral.storage.S3Provider;
import ec.twintailcs.electoral.storage.StorageException;
import ec.twintailcs.electoral.storage.StorageException.StorageErrorType;
import ec.twintailcs.electoral.storage.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import org.jboss.logging.Logger;

/**
 * Decorador de {@link StorageService} que implementa fallback automático.
 *
 * <p>Intenta con primary (ej: S3), y si falla con error retryable (conectividad, quota), cae a un
 * fallback solo si hay un proveedor alternativo registrado.
 *
 * <p><b>Configuración:</b>
 *
 * <ul>
 *   <li>Primary: Se inyecta con @S3Provider — S3StorageAdapter
 *   <li>Fallback: Se inyecta con @FallbackProvider — opcional; actualmente no hay uno registrado
 * </ul>
 *
 * <p><b>Comportamiento:</b>
 *
 * <ul>
 *   <li>Si primary.isHealthy() = false, usa fallback directamente
 *   <li>Si primary falla con error no-retryable (auth, bad request), lanza excepción
 *   <li>Si primary falla con error retryable, intenta fallback
 *   <li>Si fallback también falla, lanza excepción del fallback
 * </ul>
 *
 * <p><b>Loggeo:</b> Los fallovers se registran sin incluir mensajes de excepción ni credenciales.
 *
 * <p><b>CDI:</b> @FallbackService qualifier para distinguir esta implementación.
 * StorageConfiguration lo produce como el bean default de StorageService.
 */
@ApplicationScoped
@FallbackService
public class FallbackStorageService implements StorageService {

  private static final Logger log = Logger.getLogger(FallbackStorageService.class);

  @Inject @S3Provider Instance<StorageService> primary;

  @Inject @FallbackProvider Instance<StorageService> fallback;

  private StorageService getPrimary() {
    // Si la instancia no está disponible, retorna null
    return primary.isResolvable() ? primary.get() : null;
  }

  private StorageService getFallback() {
    return fallback.isResolvable() ? fallback.get() : null;
  }

  @Override
  public void upload(String key, InputStream input, long size, String mimeType)
      throws StorageException, IOException {
    StorageService p = getPrimary();
    if (p == null) {
      throwNoProvidersAvailable();
    }

    if (!p.isHealthy()) {
      log.warnf("Primary storage unhealthy, using fallback for upload: %s", key);
      uploadWithFallback(key, input, size, mimeType);
      return;
    }

    try {
      p.upload(key, input, size, mimeType);
    } catch (StorageException e) {
      if (e.isRetryable()) {
        fallbackAfterRetryableFailure("upload", key, e).upload(key, input, size, mimeType);
      } else {
        throw e;
      }
    }
  }

  private void uploadWithFallback(String key, InputStream input, long size, String mimeType)
      throws StorageException, IOException {
    StorageService f = getFallback();
    if (f == null) {
      throwNoProvidersAvailable();
    }
    f.upload(key, input, size, mimeType);
  }

  @Override
  public void delete(String key) throws StorageException {
    StorageService p = getPrimary();
    if (p == null) {
      throwNoProvidersAvailable();
    }

    if (!p.isHealthy()) {
      log.warnf("Primary storage unhealthy, using fallback for delete: %s", key);
      deleteWithFallback(key);
      return;
    }

    try {
      p.delete(key);
    } catch (StorageException e) {
      if (e.isRetryable()) {
        fallbackAfterRetryableFailure("delete", key, e).delete(key);
      } else {
        throw e;
      }
    }
  }

  private void deleteWithFallback(String key) throws StorageException {
    StorageService f = getFallback();
    if (f == null) {
      throwNoProvidersAvailable();
    }
    f.delete(key);
  }

  @Override
  public InputStream download(String key) throws StorageException {
    StorageService p = getPrimary();
    if (p == null) {
      throwNoProvidersAvailable();
    }

    if (!p.isHealthy()) {
      log.warnf("Primary storage unhealthy, using fallback for download: %s", key);
      return downloadWithFallback(key);
    }

    try {
      return p.download(key);
    } catch (StorageException e) {
      if (e.isRetryable()) {
        return fallbackAfterRetryableFailure("download", key, e).download(key);
      } else {
        throw e;
      }
    }
  }

  private InputStream downloadWithFallback(String key) throws StorageException {
    StorageService f = getFallback();
    if (f == null) {
      throwNoProvidersAvailable();
    }
    return f.download(key);
  }

  @Override
  public String presignDownloadUrl(String key) throws StorageException {
    StorageService p = getPrimary();
    if (p == null) {
      throwNoProvidersAvailable();
    }

    if (!p.isHealthy()) {
      log.warnf("Primary storage unhealthy, using fallback for presign download: %s", key);
      return presignDownloadUrlWithFallback(key);
    }

    try {
      return p.presignDownloadUrl(key);
    } catch (StorageException e) {
      if (e.isRetryable()) {
        return fallbackAfterRetryableFailure("presign download", key, e).presignDownloadUrl(key);
      } else {
        throw e;
      }
    }
  }

  private String presignDownloadUrlWithFallback(String key) throws StorageException {
    StorageService f = getFallback();
    if (f == null) {
      throwNoProvidersAvailable();
    }
    return f.presignDownloadUrl(key);
  }

  @Override
  public String presignUploadUrl(String key) throws StorageException {
    StorageService p = getPrimary();
    if (p == null) {
      throwNoProvidersAvailable();
    }

    if (!p.isHealthy()) {
      log.warnf("Primary storage unhealthy, using fallback for presign upload: %s", key);
      return presignUploadUrlWithFallback(key);
    }

    try {
      return p.presignUploadUrl(key);
    } catch (StorageException e) {
      if (e.isRetryable()) {
        return fallbackAfterRetryableFailure("presign upload", key, e).presignUploadUrl(key);
      } else {
        throw e;
      }
    }
  }

  private String presignUploadUrlWithFallback(String key) throws StorageException {
    StorageService f = getFallback();
    if (f == null) {
      throwNoProvidersAvailable();
    }
    return f.presignUploadUrl(key);
  }

  private StorageService fallbackAfterRetryableFailure(
      String operation, String key, StorageException primaryFailure) throws StorageException {
    StorageService f = getFallback();
    if (f == null) {
      log.warnf(
          "Primary storage %s failed (retryable), no fallback is configured: key=%s errorType=%s",
          operation, key, primaryFailure.getErrorType());
      throw primaryFailure;
    }
    log.warnf(
        "Primary storage %s failed (retryable), using fallback: key=%s errorType=%s",
        operation, key, primaryFailure.getErrorType());
    return f;
  }

  @Override
  public boolean isHealthy() {
    StorageService p = getPrimary();
    if (p != null && p.isHealthy()) {
      return true;
    }
    StorageService f = getFallback();
    return f != null && f.isHealthy();
  }

  private void throwNoProvidersAvailable() throws StorageException {
    throw new StorageException(
        "No storage providers available (primary and fallback both unavailable)",
        StorageErrorType.CONNECTIVITY_ERROR);
  }
}
