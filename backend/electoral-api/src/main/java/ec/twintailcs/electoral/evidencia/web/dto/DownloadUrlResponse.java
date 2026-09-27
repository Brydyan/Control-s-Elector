package ec.twintailcs.electoral.evidencia.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta a GET /evidencias/{id}/download-url.
 *
 * <p>El FE recibe la URL y la usa directo en un {@code <img src>}. La URL es presigned (15 min de
 * validez por default en S3StorageService).
 */
@Schema(description = "URL prefirmada S3 para descargar la evidencia (valida 15 min)")
public record DownloadUrlResponse(
    @Schema(description = "UUID publico de la evidencia") UUID id,
    @Schema(description = "URL prefirmada S3/MinIO", example = "https://s3.../obj?X-Amz-Signature=...")
        String url,
    @Schema(description = "Momento de emision de la URL (UTC)") Instant emitidoEn) {
  public DownloadUrlResponse(UUID id, String url) {
    this(id, url, Instant.now());
  }
}
