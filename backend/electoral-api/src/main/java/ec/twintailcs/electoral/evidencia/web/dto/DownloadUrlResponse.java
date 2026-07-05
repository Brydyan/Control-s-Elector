package ec.twintailcs.electoral.evidencia.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta a GET /evidencias/{id}/download-url.
 *
 * El FE recibe la URL y la usa directo en un {@code <img src>}.
 * La URL es presigned (15 min de validez por default en S3StorageService).
 */
public record DownloadUrlResponse(UUID id, String url, Instant emitidoEn) {
    public DownloadUrlResponse(UUID id, String url) {
        this(id, url, Instant.now());
    }
}
