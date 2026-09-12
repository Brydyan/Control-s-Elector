package ec.twintailcs.electoral.electoral.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EliminarPartidoUseCaseTest {

  @Mock private PartidoRepository partidoRepository;
  @Mock private AuditLogService auditLogService;

  private EliminarPartidoUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new EliminarPartidoUseCase(partidoRepository, auditLogService);
  }

  @Test
  void execute_whenNotFound_throws() {
    when(partidoRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(1L, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class)
        .hasMessageContaining("Partido no encontrado: 1");

    verify(partidoRepository, never()).delete(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_success_deletesAndAudits() {
    Partido partido = new Partido("PSC", "PSC", 6);
    partido.setId(1L);
    when(partidoRepository.findById(1L)).thenReturn(Optional.of(partido));

    useCase.execute(1L, 99L, null);

    verify(partidoRepository).delete(partido);
    verify(auditLogService)
        .registrar(
            eq(99L),
            eq("ELIMINAR_PARTIDO"),
            eq("partido"),
            eq(1L),
            eq("Partido eliminado: PSC"),
            eq(null));
  }
}
