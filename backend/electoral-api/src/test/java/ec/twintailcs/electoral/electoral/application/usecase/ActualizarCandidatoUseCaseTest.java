package ec.twintailcs.electoral.electoral.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActualizarCandidatoUseCaseTest {

  @Mock private CandidatoRepository candidatoRepository;
  @Mock private CargoRepository cargoRepository;
  @Mock private PartidoRepository partidoRepository;
  @Mock private AuditLogService auditLogService;

  private ActualizarCandidatoUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new ActualizarCandidatoUseCase(
            candidatoRepository, cargoRepository, partidoRepository, auditLogService);
  }

  @Test
  void execute_whenCandidatoNotFound_throwsNotFoundException() {
    when(candidatoRepository.findById(5L)).thenReturn(Optional.empty());
    CandidatoRequest dto = new CandidatoRequest("Juan Actualizado", 2, 10L, 20L);

    assertThatThrownBy(() -> useCase.execute(5L, dto, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class)
        .hasMessageContaining("Candidato no encontrado: 5");

    verify(candidatoRepository, never()).save(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_whenCargoNotFound_throwsNotFoundException() {
    Candidato candidato = new Candidato("Juan Original", 1, null, null);
    candidato.setId(5L);
    when(candidatoRepository.findById(5L)).thenReturn(Optional.of(candidato));
    when(cargoRepository.findById(10L)).thenReturn(Optional.empty());

    CandidatoRequest dto = new CandidatoRequest("Juan Actualizado", 2, 10L, 20L);

    assertThatThrownBy(() -> useCase.execute(5L, dto, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class)
        .hasMessageContaining("Cargo no encontrado: 10");

    verify(candidatoRepository, never()).save(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_whenPartidoNotFound_throwsNotFoundException() {
    Cargo cargo = new Cargo("Concejal", NivelCargo.CANTON, 3);
    cargo.setId(10L);
    Candidato candidato = new Candidato("Juan Original", 1, null, null);
    candidato.setId(5L);

    when(candidatoRepository.findById(5L)).thenReturn(Optional.of(candidato));
    when(cargoRepository.findById(10L)).thenReturn(Optional.of(cargo));
    when(partidoRepository.findById(20L)).thenReturn(Optional.empty());

    CandidatoRequest dto = new CandidatoRequest("Juan Actualizado", 2, 10L, 20L);

    assertThatThrownBy(() -> useCase.execute(5L, dto, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class)
        .hasMessageContaining("Partido no encontrado: 20");

    verify(candidatoRepository, never()).save(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_success_updatesAndAudits() {
    Cargo cargo = new Cargo("Concejal", NivelCargo.CANTON, 3);
    cargo.setId(10L);
    Partido partido = new Partido("ID", "ID", 12);
    partido.setId(20L);
    Candidato candidato = new Candidato("Juan Original", 1, null, null);
    candidato.setId(5L);

    when(candidatoRepository.findById(5L)).thenReturn(Optional.of(candidato));
    when(cargoRepository.findById(10L)).thenReturn(Optional.of(cargo));
    when(partidoRepository.findById(20L)).thenReturn(Optional.of(partido));
    when(candidatoRepository.save(any(Candidato.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CandidatoRequest dto = new CandidatoRequest("Juan Actualizado", 2, 10L, 20L);
    Candidato resultado = useCase.execute(5L, dto, 99L, null);

    assertThat(resultado).isNotNull();
    assertThat(resultado.getId()).isEqualTo(5L);
    assertThat(resultado.getNombre()).isEqualTo("Juan Actualizado");
    assertThat(resultado.getOrden()).isEqualTo(2);
    assertThat(resultado.getCargo()).isEqualTo(cargo);
    assertThat(resultado.getPartido()).isEqualTo(partido);

    verify(candidatoRepository).save(candidato);
    verify(auditLogService)
        .registrarConIp(
            eq(99L),
            eq("ACTUALIZAR_CANDIDATO"),
            eq("candidato"),
            eq(5L),
            eq("Candidato actualizado: Juan Actualizado"),
            eq(null));
  }
}
