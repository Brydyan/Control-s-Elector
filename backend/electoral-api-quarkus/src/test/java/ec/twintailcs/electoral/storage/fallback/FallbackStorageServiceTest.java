package ec.twintailcs.electoral.storage.fallback;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ec.twintailcs.electoral.storage.StorageException;
import ec.twintailcs.electoral.storage.StorageException.StorageErrorType;
import ec.twintailcs.electoral.storage.StorageService;
import jakarta.enterprise.inject.Instance;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class FallbackStorageServiceTest {

  @Test
  void propagatesOriginalFailureWhenNoFallbackIsResolvable() {
    StorageException expected =
        new StorageException("Primary storage unavailable", StorageErrorType.CONNECTIVITY_ERROR);
    StorageService primary = new FailingStorageService(expected);
    FallbackStorageService service = new FallbackStorageService();
    service.primary = instanceOf(primary);
    service.fallback = unresolvedInstance();

    StorageException actual =
        assertThrows(StorageException.class, () -> service.presignDownloadUrl("actas/1/foto.webp"));

    assertSame(expected, actual);
  }

  @SuppressWarnings("unchecked")
  private static Instance<StorageService> instanceOf(StorageService service) {
    return (Instance<StorageService>)
        Proxy.newProxyInstance(
            Instance.class.getClassLoader(),
            new Class<?>[] {Instance.class},
            (proxy, method, args) -> {
              if (method.getName().equals("isResolvable")) {
                return true;
              }
              if (method.getName().equals("get")) {
                return service;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  @SuppressWarnings("unchecked")
  private static Instance<StorageService> unresolvedInstance() {
    return (Instance<StorageService>)
        Proxy.newProxyInstance(
            Instance.class.getClassLoader(),
            new Class<?>[] {Instance.class},
            (proxy, method, args) -> {
              if (method.getName().equals("isResolvable")) {
                return false;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static final class FailingStorageService implements StorageService {
    private final StorageException failure;

    private FailingStorageService(StorageException failure) {
      this.failure = failure;
    }

    @Override
    public void upload(String key, InputStream input, long size, String mimeType)
        throws StorageException, IOException {
      throw failure;
    }

    @Override
    public void delete(String key) throws StorageException {
      throw failure;
    }

    @Override
    public InputStream download(String key) throws StorageException {
      throw failure;
    }

    @Override
    public String presignDownloadUrl(String key) throws StorageException {
      throw failure;
    }

    @Override
    public String presignUploadUrl(String key) throws StorageException {
      throw failure;
    }

    @Override
    public boolean isHealthy() {
      return true;
    }
  }
}
