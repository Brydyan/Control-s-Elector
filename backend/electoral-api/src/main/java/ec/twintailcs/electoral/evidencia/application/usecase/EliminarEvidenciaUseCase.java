package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EliminarEvidenciaUseCase {

  private static final Logger log = LoggerFactory.getLogger(EliminarEvidenciaUseCase.class);

  private final EvidenciaRepository repository;
  private final S3StorageService s3;

  public EliminarEvidenciaUseCase(EvidenciaRepository repository, S3StorageService s3) {
    this.repository = repository;
    this.s3 = s3;
  }

  @Transactional
  public void execute(UUID publicId, UUID callerPublicId) {
    Evidencia e =
        repository
            .findByPublicId(publicId)
            .orElseThrow(() -> new EvidenciaNotFoundException(publicId));

    repository.delete(e);
    try {
      s3.delete(e.getS3Key());
    } catch (Exception s3Err) {
      log.error(
          "S3 delete failed for evidencia with public ID {} (key {}); queda orphan",
          publicId,
          e.getS3Key(),
          s3Err);
    }
  }
}
