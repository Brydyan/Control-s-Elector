package ec.twintailcs.electoral.evidencia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de {@code GET /api/v1/evidencias/{id}/download-url}.
 *
 * <p>El frontend recibe la URL y la usa directo en un {@code <img src>}. La URL es prefirmada (15
 * min de validez por default en el servicio de storage).
 */
@RegisterForReflection
public record DownloadUrlResponse(UUID id, String url, Instant emitidoEn) {

  public DownloadUrlResponse(UUID id, String url) {
    this(id, url, Instant.now());
  }
}
