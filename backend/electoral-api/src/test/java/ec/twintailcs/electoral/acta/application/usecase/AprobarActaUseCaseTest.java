package ec.twintailcs.electoral.acta.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AprobarActaUseCaseTest {

  @Mock private ActaRepository actaRepository;
  @Mock private AuditLogService auditLogService;

  private AprobarActaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new AprobarActaUseCase(actaRepository, auditLogService);
  }

  @Test
  void execute_whenNotFound_throws() {
    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(1L, 99L, "127.0.0.1"))
        .isInstanceOf(ActaInvalidaException.class)
        .hasMessageContaining("Acta no encontrada: 1");

    verify(actaRepository, never()).save(any());
  }

  @Test
  void execute_whenEstadoInvalido_throws() {
    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.BORRADOR);
    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(acta));

    assertThatThrownBy(() -> useCase.execute(1L, 99L, "127.0.0.1"))
        .isInstanceOf(ActaEstadoInvalidoException.class);

    verify(actaRepository, never()).save(any());
  }

  @Test
  void execute_success_approvesAndAudits() {
    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.ENVIADA);
    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(acta));
    when(actaRepository.save(any(Acta.class))).thenAnswer(i -> i.getArgument(0));

    Acta resultado = useCase.execute(1L, 99L, "127.0.0.1");

    assertThat(resultado.getEstado()).isEqualTo(EstadoActa.APROBADA);
    verify(actaRepository).save(acta);
    verify(auditLogService)
        .registrarConIp(
            eq(99L),
            eq("ACTA_APROBADA"),
            eq("acta"),
            eq(1L),
            eq("Acta aprobada por coordinator 99"),
            eq("127.0.0.1"));
  }
}
