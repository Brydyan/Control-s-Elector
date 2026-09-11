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
import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrearCandidatoUseCaseTest {

  @Mock private CandidatoRepository candidatoRepository;
  @Mock private CargoRepository cargoRepository;
  @Mock private PartidoRepository partidoRepository;
  @Mock private AuditLogService auditLogService;

  private CrearCandidatoUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new CrearCandidatoUseCase(
            candidatoRepository, cargoRepository, partidoRepository, auditLogService);
  }

  @Test
  void execute_whenCargoNotFound_throwsNotFoundException() {
    when(cargoRepository.findById(10L)).thenReturn(Optional.empty());
    CandidatoRequest dto = new CandidatoRequest("Juan Perez", 1, 10L, 20L);

    assertThatThrownBy(() -> useCase.execute(dto, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class)
        .hasMessageContaining("Cargo no encontrado: 10");

    verify(candidatoRepository, never()).save(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_whenPartidoNotFound_throwsNotFoundException() {
    Cargo cargo = new Cargo("Alcalde/sa", NivelCargo.CANTON, 1);
    cargo.setId(10L);
    when(cargoRepository.findById(10L)).thenReturn(Optional.of(cargo));
    when(partidoRepository.findById(20L)).thenReturn(Optional.empty());
    CandidatoRequest dto = new CandidatoRequest("Juan Perez", 1, 10L, 20L);

    assertThatThrownBy(() -> useCase.execute(dto, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class)
        .hasMessageContaining("Partido no encontrado: 20");

    verify(candidatoRepository, never()).save(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_success_savesAndAudits() {
    Cargo cargo = new Cargo("Alcalde/sa", NivelCargo.CANTON, 1);
    cargo.setId(10L);
    Partido partido = new Partido("PSC", "PSC", 6);
    partido.setId(20L);

    when(cargoRepository.findById(10L)).thenReturn(Optional.of(cargo));
    when(partidoRepository.findById(20L)).thenReturn(Optional.of(partido));

    CandidatoRequest dto = new CandidatoRequest("Juan Perez", 1, 10L, 20L);
    Candidato guardado = new Candidato(dto.nombre(), dto.orden(), cargo, partido);
    guardado.setId(1L);
    when(candidatoRepository.save(any(Candidato.class))).thenReturn(guardado);

    Candidato resultado = useCase.execute(dto, 99L, null);

    assertThat(resultado).isNotNull();
    assertThat(resultado.getId()).isEqualTo(1L);
    assertThat(resultado.getNombre()).isEqualTo("Juan Perez");
    assertThat(resultado.getOrden()).isEqualTo(1);
    assertThat(resultado.getCargo()).isEqualTo(cargo);
    assertThat(resultado.getPartido()).isEqualTo(partido);

    verify(candidatoRepository).save(any(Candidato.class));
    verify(auditLogService)
        .registrar(
            eq(99L),
            eq("CREAR_CANDIDATO"),
            eq("candidato"),
            eq(1L),
            eq("Candidato: Juan Perez para cargo Alcalde/sa"),
            eq(null));
  }
}
