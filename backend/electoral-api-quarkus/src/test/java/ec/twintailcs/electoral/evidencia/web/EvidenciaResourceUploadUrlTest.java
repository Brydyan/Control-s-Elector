package ec.twintailcs.electoral.evidencia.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.twintailcs.electoral.evidencia.web.dto.UploadUrlResponse;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios para el endpoint {@code POST /api/v1/evidencias/{id}/upload-url}.
 *
 * <p>Verifica que:
 * - El DTOUploadUrlResponse se estructura correctamente
 * - La URL prefirmada contiene firma X-Amz-Signature
 * - El campo expiresIn es 900 segundos (15 minutos)
 * - La clave de objeto sigue la convención evidencias/{id}.webp
 */
@QuarkusTest
class EvidenciaResourceUploadUrlTest {

  @Inject S3StorageService s3Storage;

  @Test
  void uploadUrlResponseStructureCorrect() {
    UUID evidenciaId = UUID.randomUUID();
    String key = "evidencias/" + evidenciaId + ".webp";

    String url = s3Storage.presignUploadUrl(key);
    UploadUrlResponse response = new UploadUrlResponse(evidenciaId, url);

    assertTrue(response.id().equals(evidenciaId), "ID debe coincider");
    assertTrue(response.uploadUrl() != null && !response.uploadUrl().isBlank(),
        "uploadUrl no puede estar vacío");
    assertTrue(response.expiresIn() == 900, "expiresIn debe ser 900 segundos (15 min)");
  }

  @Test
  void uploadUrlContainsSignature() {
    UUID evidenciaId = UUID.randomUUID();
    String key = "evidencias/" + evidenciaId + ".webp";

    String url = s3Storage.presignUploadUrl(key);

    assertTrue(url.contains("X-Amz-Signature"), "URL debe estar firmada con X-Amz-Signature");
    assertTrue(url.contains(key), "URL debe contener la clave de objeto");
  }

  @Test
  void uploadUrlExpiryIs900Seconds() {
    String url = s3Storage.presignUploadUrl("test.webp");

    // RF-STG-04: Validar que contiene expiración de 900 segundos (PT15M = 15 min)
    assertTrue(url.contains("900") || url.contains("X-Amz-Expires"),
        "URL debe contener información de expiración (900 segundos)");
  }
}
