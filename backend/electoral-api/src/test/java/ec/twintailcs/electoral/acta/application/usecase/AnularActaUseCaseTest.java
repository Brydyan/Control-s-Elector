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
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.notificacion.application.NotificacionService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnularActaUseCaseTest {

  @Mock private ActaRepository actaRepository;
  @Mock private NotificacionService notificacionService;
  @Mock private AuditLogService auditLogService;

  private AnularActaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new AnularActaUseCase(actaRepository, notificacionService, auditLogService);
  }

  @Test
  void execute_whenNotFound_throws() {
    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(1L, 99L, "Fraude", "127.0.0.1"))
        .isInstanceOf(ActaInvalidaException.class);

    verify(actaRepository, never()).save(any());
  }

  @Test
  void execute_whenNotAprobada_throws() {
    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.ENVIADA);
    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(acta));

    assertThatThrownBy(() -> useCase.execute(1L, 99L, "Fraude", "127.0.0.1"))
        .isInstanceOf(ActaEstadoInvalidoException.class);

    verify(actaRepository, never()).save(any());
  }

  @Test
  void execute_success_anulaAndAudits() {
    Usuario autor = new Usuario();
    autor.setId(5L);
    Jrv jrv = new Jrv();
    jrv.setId(10L);

    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.APROBADA);
    acta.setRegistradaPor(autor);
    acta.setJrv(jrv);

    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(acta));
    when(actaRepository.save(any(Acta.class))).thenAnswer(i -> i.getArgument(0));

    Acta resultado = useCase.execute(1L, 99L, "Fraude comprobado", "127.0.0.1");

    assertThat(resultado.getEstado()).isEqualTo(EstadoActa.ANULADA);
    assertThat(resultado.getMotivoAnulacion()).isEqualTo("Fraude comprobado");
    verify(actaRepository).save(acta);
    verify(notificacionService).notificar(eq(autor), eq("ACTA_ANULADA"), eq(acta), any());
    verify(auditLogService)
        .registrarConIp(
            eq(99L),
            eq("ACTA_ANULADA"),
            eq("acta"),
            eq(1L),
            eq("Motivo: Fraude comprobado"),
            eq("127.0.0.1"));
  }
}
