package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarEvidenciaUseCase {

  private final EvidenciaRepository repository;
  private final S3StorageService s3;

  public ConsultarEvidenciaUseCase(EvidenciaRepository repository, S3StorageService s3) {
    this.repository = repository;
    this.s3 = s3;
  }

  @Transactional(readOnly = true)
  public Evidencia get(UUID publicId) {
    return repository
        .findByPublicId(publicId)
        .orElseThrow(() -> new EvidenciaNotFoundException(publicId));
  }

  @Transactional(readOnly = true)
  public String presignDownload(UUID publicId) {
    Evidencia e = get(publicId);
    return s3.presignDownloadUrl(e.getS3Key());
  }
}
