package ec.twintailcs.electoral.evidencia.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class ConsultarEvidenciaUseCaseTest {

  @Mock private EvidenciaRepository repository;
  @Mock private S3StorageService s3;

  private ConsultarEvidenciaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new ConsultarEvidenciaUseCase(repository, s3);
  }

  @Test
  void get_notExists_throwsNotFound() {
    UUID publicId = UUID.randomUUID();
    when(repository.findByPublicId(publicId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.get(publicId)).isInstanceOf(EvidenciaNotFoundException.class);
  }

  @Test
  void get_exists_returnsEvidencia() {
    UUID publicId = UUID.randomUUID();
    Evidencia e = new Evidencia();
    e.setPublicId(publicId);
    when(repository.findByPublicId(publicId)).thenReturn(Optional.of(e));

    Evidencia res = useCase.get(publicId);
    assertThat(res.getPublicId()).isEqualTo(publicId);
  }

  @Test
  void presignDownload_exists_returnsUrl() {
    UUID publicId = UUID.randomUUID();
    Evidencia e = new Evidencia();
    e.setPublicId(publicId);
    e.setS3Key("path/file.webp");
    when(repository.findByPublicId(publicId)).thenReturn(Optional.of(e));
    when(s3.presignDownloadUrl("path/file.webp")).thenReturn("https://s3.example.com/url");

    String url = useCase.presignDownload(publicId);
    assertThat(url).isEqualTo("https://s3.example.com/url");
    verify(s3).presignDownloadUrl("path/file.webp");
  }
}
