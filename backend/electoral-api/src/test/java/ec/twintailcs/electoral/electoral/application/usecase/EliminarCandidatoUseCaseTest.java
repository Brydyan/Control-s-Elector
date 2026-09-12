package ec.twintailcs.electoral.electoral.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EliminarCandidatoUseCaseTest {

  @Mock private CandidatoRepository candidatoRepository;
  @Mock private AuditLogService auditLogService;

  private EliminarCandidatoUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new EliminarCandidatoUseCase(candidatoRepository, auditLogService);
  }

  @Test
  void execute_whenNotFound_throwsNotFoundException() {
    when(candidatoRepository.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(7L, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class)
        .hasMessageContaining("Candidato no encontrado: 7");

    verify(candidatoRepository, never()).delete(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_success_deletesAndAudits() {
    Candidato candidato = new Candidato("Maria Lopez", 1, null, null);
    candidato.setId(7L);
    when(candidatoRepository.findById(7L)).thenReturn(Optional.of(candidato));

    useCase.execute(7L, 99L, null);

    verify(candidatoRepository).delete(candidato);
    verify(auditLogService)
        .registrarConIp(
            eq(99L),
            eq("ELIMINAR_CANDIDATO"),
            eq("candidato"),
            eq(7L),
            eq("Candidato eliminado: Maria Lopez"),
            eq(null));
  }
}
