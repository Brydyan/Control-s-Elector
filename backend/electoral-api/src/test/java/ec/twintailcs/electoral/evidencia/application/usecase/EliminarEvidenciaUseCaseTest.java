package ec.twintailcs.electoral.evidencia.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EliminarEvidenciaUseCaseTest {

  @Mock private EvidenciaRepository repository;
  @Mock private S3StorageService s3;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private AuditLogService auditLogService;

  private EliminarEvidenciaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new EliminarEvidenciaUseCase(repository, s3, usuarioRepository, auditLogService);
  }

  @Test
  void execute_notExists_throwsNotFound() {
    UUID publicId = UUID.randomUUID();
    when(repository.findByPublicId(publicId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(publicId, UUID.randomUUID()))
        .isInstanceOf(EvidenciaNotFoundException.class);
  }

  @Test
  void execute_success_deletesDbAndS3AndAudits() {
    UUID publicId = UUID.randomUUID();
    UUID callerPublicId = UUID.randomUUID();
    Usuario caller = new Usuario();
    caller.setId(77L);
    when(usuarioRepository.findByPublicId(callerPublicId)).thenReturn(Optional.of(caller));

    Evidencia e = new Evidencia();
    e.setId(10L);
    e.setPublicId(publicId);
    e.setS3Key("path/file.webp");
    when(repository.findByPublicId(publicId)).thenReturn(Optional.of(e));

    useCase.execute(publicId, callerPublicId);

    verify(repository).delete(e);
    verify(s3).delete("path/file.webp");
    verify(auditLogService)
        .registrar(eq(77L), eq("EVIDENCIA_ELIMINADA"), eq("evidencia"), eq(10L), any());
  }

  @Test
  void execute_s3Fails_doesNotThrow() {
    UUID publicId = UUID.randomUUID();
    Evidencia e = new Evidencia();
    e.setId(10L);
    e.setPublicId(publicId);
    e.setS3Key("path/file.webp");
    when(repository.findByPublicId(publicId)).thenReturn(Optional.of(e));
    doThrow(new RuntimeException("s3 error")).when(s3).delete("path/file.webp");

    useCase.execute(publicId, null);

    verify(repository).delete(e);
    verify(s3).delete("path/file.webp");
    verify(auditLogService)
        .registrar(eq(null), eq("EVIDENCIA_ELIMINADA"), eq("evidencia"), eq(10L), any());
  }
}
