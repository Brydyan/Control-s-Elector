package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * Consulta de la metadata de una evidencia y de su URL de descarga.
 *
 * <p>Port exacto del {@code ConsultarEvidenciaUseCase} del backend Spring.
 */
@ApplicationScoped
public class ConsultarEvidenciaUseCase {

  @Inject S3StorageService s3;

  /** Metadata de la evidencia por su UUID publico. */
  @Transactional
  public Evidencia get(UUID publicId) {
    Evidencia evidencia = Evidencia.find("publicId", publicId).firstResult();
    if (evidencia == null) {
      throw new EvidenciaNotFoundException(publicId);
    }
    return evidencia;
  }

  /** URL prefirmada de descarga del objeto de la evidencia. */
  @Transactional
  public String presignDownload(UUID publicId) {
    Evidencia evidencia = get(publicId);
    return s3.presignDownloadUrl(evidencia.getS3Key());
  }
}
