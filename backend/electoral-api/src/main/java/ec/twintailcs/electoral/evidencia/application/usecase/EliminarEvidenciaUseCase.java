package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EliminarEvidenciaUseCase {

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

    // B.4 — El delete NO debe tragarse el error de S3. Si S3 falla, el FE recibe 5xx y puede
    // reintentar. Antes, el catch logueaba y devolvía éxito, dejando la foto accesible mientras
    // el usuario pensaba que se había borrado.
    s3.delete(e.getS3Key());

    auditLogService.registrar(
        callerId,
        "EVIDENCIA_ELIMINADA",
        "evidencia",
        e.getId(),
        "Evidencia eliminada publicId=" + publicId);
  }
}
