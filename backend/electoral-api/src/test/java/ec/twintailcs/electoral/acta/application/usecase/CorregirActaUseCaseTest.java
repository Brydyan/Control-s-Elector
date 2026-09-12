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
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CorregirActaUseCaseTest {

  @Mock private ActaRepository actaRepository;
  @Mock private ActaDetalleRepository actaDetalleRepository;
  @Mock private CandidatoRepository candidatoRepository;
  @Mock private AuditLogService auditLogService;

  private CorregirActaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new CorregirActaUseCase(
            actaRepository, actaDetalleRepository, candidatoRepository, auditLogService);
  }

  @Test
  void execute_whenNotFound_throws() {
    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.empty());

    ActaRequestDto dto = new ActaRequestDto(1L, 2L, 10, 0, 0, List.of());

    assertThatThrownBy(() -> useCase.execute(1L, dto, 99L, true, "127.0.0.1"))
        .isInstanceOf(ActaInvalidaException.class);

    verify(actaRepository, never()).save(any());
  }

  @Test
  void execute_whenNotEnDisputa_throws() {
    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.ENVIADA);
    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(acta));

    ActaRequestDto dto = new ActaRequestDto(1L, 2L, 10, 0, 0, List.of());

    assertThatThrownBy(() -> useCase.execute(1L, dto, 99L, true, "127.0.0.1"))
        .isInstanceOf(ActaEstadoInvalidoException.class);
  }

  @Test
  void execute_whenNotCoordinatorAndNotOwner_throws() {
    Usuario autor = new Usuario();
    autor.setId(5L);

    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.EN_DISPUTA);
    acta.setRegistradaPor(autor);
    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(acta));

    ActaRequestDto dto = new ActaRequestDto(1L, 2L, 10, 0, 0, List.of());

    assertThatThrownBy(() -> useCase.execute(1L, dto, 99L, false, "127.0.0.1"))
        .isInstanceOf(AccesoNoPermitidoException.class);
  }

  @Test
  void execute_success_corregirAndAudits() {
    Usuario autor = new Usuario();
    autor.setId(99L);
    Cargo cargo = new Cargo();
    cargo.setId(10L);

    Acta acta = new Acta();
    acta.setId(1L);
    acta.setEstado(EstadoActa.EN_DISPUTA);
    acta.setRegistradaPor(autor);
    acta.setCargo(cargo);

    Candidato c1 = new Candidato();
    c1.setId(101L);
    c1.setCargo(cargo);

    ActaDetalleDto detalleDto = new ActaDetalleDto(101L, 80);
    ActaRequestDto dto = new ActaRequestDto(1L, 10L, 100, 10, 10, List.of(detalleDto));

    when(actaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(acta));
    when(candidatoRepository.findById(101L)).thenReturn(Optional.of(c1));
    when(actaRepository.save(any(Acta.class))).thenAnswer(i -> i.getArgument(0));

    Acta resultado = useCase.execute(1L, dto, 99L, false, "127.0.0.1");

    assertThat(resultado.getEstado()).isEqualTo(EstadoActa.CORREGIDA);
    assertThat(resultado.getTotalSufragantes()).isEqualTo(100);
    verify(actaDetalleRepository).deleteByActa(acta);
    verify(actaRepository).save(acta);
    verify(auditLogService)
        .registrarConIp(
            eq(99L),
            eq("ACTA_CORREGIDA"),
            eq("acta"),
            eq(1L),
            eq("Acta corregida por usuario 99"),
            eq("127.0.0.1"));
  }
}
