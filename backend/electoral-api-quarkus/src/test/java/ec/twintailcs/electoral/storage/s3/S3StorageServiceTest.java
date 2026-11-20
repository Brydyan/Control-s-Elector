package ec.twintailcs.electoral.storage.s3;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios para {@link S3StorageService}.
 *
 * <p>Verifica que las URLs prefirmadas se generan correctamente con: - Expiración: 15 minutos
 * (PT15M) - Firma: contiene X-Amz-Signature - Path: contiene la clave esperada
 */
@QuarkusTest
class S3StorageServiceTest {

  @Inject S3StorageService service;

  @Test
  void presignUploadUrlContainsExpiry() {
    String url = service.presignUploadUrl("test-evidencia-id.webp");

    assertTrue(url.contains("X-Amz-Expires"), "URL debe contener X-Amz-Expires");
    assertTrue(url.contains("X-Amz-Signature"), "URL debe estar firmada");
  }

  @Test
  void presignUploadUrlPathCorrect() {
    String key = "evidencias/test-123.webp";
    String url = service.presignUploadUrl(key);

    assertTrue(url.contains(key), "URL debe contener la clave de objeto");
  }

  @Test
  void presignDownloadUrlContainsExpiry() {
    String url = service.presignDownloadUrl("test-evidencia-id.webp");

    assertTrue(url.contains("X-Amz-Expires"), "URL debe contener X-Amz-Expires");
    assertTrue(url.contains("X-Amz-Signature"), "URL debe estar firmada");
  }

  @Test
  void presignDownloadUrlPathCorrect() {
    String key = "evidencias/test-123.webp";
    String url = service.presignDownloadUrl(key);

    assertTrue(url.contains(key), "URL debe contener la clave de objeto");
  }

  @Test
  void expiryIs15Minutes() {
    String url = service.presignUploadUrl("test.webp");

    // RF-STG-04: validar que la expiración es 15 minutos (900 segundos)
    assertTrue(url.contains("900") || url.contains("15m"), "URL debe expirar en 15 minutos (900s)");
  }
}
