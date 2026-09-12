package ec.twintailcs.electoral.evidencia.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CambiarEstadoEvidenciaUseCaseTest {

  @Mock private EvidenciaRepository repository;

  private CambiarEstadoEvidenciaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CambiarEstadoEvidenciaUseCase(repository);
  }

  @Test
  void execute_notExists_throwsNotFound() {
    UUID publicId = UUID.randomUUID();
    when(repository.findByPublicId(publicId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(publicId, EstadoEvidencia.APROBADA))
        .isInstanceOf(EvidenciaNotFoundException.class);
  }

  @Test
  void execute_invalidEstado_throwsInvalida() {
    UUID publicId = UUID.randomUUID();
    Evidencia evidencia = new Evidencia();
    when(repository.findByPublicId(publicId)).thenReturn(Optional.of(evidencia));

    assertThatThrownBy(() -> useCase.execute(publicId, EstadoEvidencia.EN_REVISION))
        .isInstanceOf(EvidenciaInvalidaException.class);
  }

  @Test
  void execute_success_updatesEstado() {
    UUID publicId = UUID.randomUUID();
    Evidencia evidencia = new Evidencia();
    evidencia.setPublicId(publicId);
    when(repository.findByPublicId(publicId)).thenReturn(Optional.of(evidencia));
    when(repository.save(any(Evidencia.class))).thenAnswer(i -> i.getArgument(0));

    Evidencia res = useCase.execute(publicId, EstadoEvidencia.APROBADA);

    assertThat(res.getEstado()).isEqualTo(EstadoEvidencia.APROBADA);
    verify(repository).save(evidencia);
  }
}
