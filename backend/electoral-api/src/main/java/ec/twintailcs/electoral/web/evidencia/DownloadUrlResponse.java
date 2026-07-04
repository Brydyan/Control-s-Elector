package ec.twintailcs.electoral.web.evidencia;

import java.time.Instant;

/**
 * Respuesta a GET /evidencias/{id}/download-url.
 *
 * El FE recibe la URL y la usa directo en un {@code <img src>}.
 * La URL es presigned (15 min de validez por default en S3StorageService).
 */
public record DownloadUrlResponse(Long id, String url, Instant emitidoEn) {
    public DownloadUrlResponse(Long id, String url) {
        this(id, url, Instant.now());
    }
}
