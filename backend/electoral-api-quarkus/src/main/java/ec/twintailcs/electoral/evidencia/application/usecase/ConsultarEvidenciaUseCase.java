package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.storage.StorageException;
import ec.twintailcs.electoral.storage.StorageService;
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

  @Inject StorageService storage;

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
  public String presignDownload(UUID publicId) throws StorageException {
    Evidencia evidencia = get(publicId);
    return storage.presignDownloadUrl(evidencia.getS3Key());
  }
}
