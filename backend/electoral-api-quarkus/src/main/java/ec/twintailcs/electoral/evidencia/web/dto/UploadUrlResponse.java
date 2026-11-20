package ec.twintailcs.electoral.evidencia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;

/**
 * Respuesta de {@code POST /api/v1/evidencias/{id}/upload-url}.
 *
 * <p>El frontend recibe la URL prefirmada y la usa para PUT-ear el archivo directamente a R2 sin
 * pasar por el backend. La URL expira en {@code expiresIn} segundos (default 15 min = 900 seg).
 * RF-STG-04.
 */
@RegisterForReflection
public record UploadUrlResponse(UUID id, String uploadUrl, long expiresIn) {

  public UploadUrlResponse(UUID id, String uploadUrl) {
    this(id, uploadUrl, 900); // 15 min default
  }
}
