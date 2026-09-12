package ec.twintailcs.electoral.acta.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.acta.domain.AccesoNoPermitidoException;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalle;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnviarActaUseCaseTest {

  @Mock private ActaRepository actaRepository;
  @Mock private ActaDetalleRepository actaDetalleRepository;
  @Mock private AuditLogService auditLogService;

  private EnviarActaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new EnviarActaUseCase(actaRepository, actaDetalleRepository, auditLogService);
  }

  @Test
  void execute_whenNotFound_throws() {
    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(1L, 99L, "127.0.0.1"))
        .isInstanceOf(ActaInvalidaException.class);

    verify(actaRepository, never()).save(any());
  }

  @Test
  void execute_whenNotBorrador_throws() {
    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.ENVIADA);
    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(acta));

    assertThatThrownBy(() -> useCase.execute(1L, 99L, "127.0.0.1"))
        .isInstanceOf(ActaEstadoInvalidoException.class);
  }

  @Test
  void execute_whenNotOwner_throws() {
    Usuario autor = new Usuario();
    autor.setId(5L);

    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.BORRADOR);
    acta.setRegistradaPor(autor);

    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(acta));

    assertThatThrownBy(() -> useCase.execute(1L, 99L, "127.0.0.1"))
        .isInstanceOf(AccesoNoPermitidoException.class);
  }

  @Test
  void execute_success_enviarAndAudits() {
    Usuario autor = new Usuario();
    autor.setId(99L);
    Cargo cargo = new Cargo();
    cargo.setId(10L);

    Jrv jrv = new Jrv();
    jrv.setId(5L);

    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.BORRADOR);
    acta.setRegistradaPor(autor);
    acta.setJrv(jrv);
    acta.setCargo(cargo);
    acta.setTotalSufragantes(100);
    acta.setVotosBlancos(10);
    acta.setVotosNulos(10);

    Candidato c1 = new Candidato();
    c1.setId(101L);
    c1.setCargo(cargo);
    ActaDetalle d1 = new ActaDetalle(acta, c1, 80);

    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(acta));
    when(actaDetalleRepository.findByActa(acta)).thenReturn(List.of(d1));
    when(actaRepository.save(any(Acta.class))).thenAnswer(i -> i.getArgument(0));

    Acta resultado = useCase.execute(1L, 99L, "127.0.0.1");

    assertThat(resultado.getEstado()).isEqualTo(EstadoActa.ENVIADA);
    verify(actaRepository).save(acta);
    verify(auditLogService)
        .registrarConIp(
            eq(99L),
            eq("ACTA_ENVIADA"),
            eq("acta"),
            eq(1L),
            eq("Borrador oficializado"),
            eq("127.0.0.1"));
  }
}
