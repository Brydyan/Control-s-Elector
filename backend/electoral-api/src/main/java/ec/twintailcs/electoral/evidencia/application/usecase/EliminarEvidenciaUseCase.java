package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
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
  private final UsuarioRepository usuarioRepository;
  private final AuditLogService auditLogService;

  public EliminarEvidenciaUseCase(
      EvidenciaRepository repository,
      S3StorageService s3,
      UsuarioRepository usuarioRepository,
      AuditLogService auditLogService) {
    this.repository = repository;
    this.s3 = s3;
    this.usuarioRepository = usuarioRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public void execute(UUID publicId, UUID callerPublicId) {
    Evidencia e =
        repository
            .findByPublicId(publicId)
            .orElseThrow(() -> new EvidenciaNotFoundException(publicId));

    Long callerId = null;
    if (callerPublicId != null) {
      callerId = usuarioRepository.findByPublicId(callerPublicId).map(Usuario::getId).orElse(null);
    }

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

    auditLogService.registrar(
        callerId,
        "EVIDENCIA_ELIMINADA",
        "evidencia",
        e.getId(),
        "Evidencia eliminada publicId=" + publicId);
  }
}
