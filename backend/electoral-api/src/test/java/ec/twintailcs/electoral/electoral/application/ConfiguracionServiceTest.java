package ec.twintailcs.electoral.electoral.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.application.usecase.ActualizarCandidatoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.CrearCandidatoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.EliminarCandidatoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.ListarCandidatosUseCase;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.ConfiguracionConflictException;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfiguracionServiceTest {

  @Mock private CargoRepository cargoRepository;
  @Mock private PartidoRepository partidoRepository;
  @Mock private CandidatoRepository candidatoRepository;
  @Mock private AuditLogService auditLogService;

  private ConfiguracionService service;

  @BeforeEach
  void setUp() {
    CrearCandidatoUseCase crearCandidatoUseCase =
        new CrearCandidatoUseCase(
            candidatoRepository, cargoRepository, partidoRepository, auditLogService);
    ActualizarCandidatoUseCase actualizarCandidatoUseCase =
        new ActualizarCandidatoUseCase(
            candidatoRepository, cargoRepository, partidoRepository, auditLogService);
    EliminarCandidatoUseCase eliminarCandidatoUseCase =
        new EliminarCandidatoUseCase(candidatoRepository, auditLogService);
    ListarCandidatosUseCase listarCandidatosUseCase =
        new ListarCandidatosUseCase(candidatoRepository);

    service =
        new ConfiguracionService(
            cargoRepository,
            partidoRepository,
            auditLogService,
            crearCandidatoUseCase,
            actualizarCandidatoUseCase,
            eliminarCandidatoUseCase,
            listarCandidatosUseCase);
  }

  @Test
  void createCargo_savesAndAudits() {
    CargoRequest dto = new CargoRequest("Alcalde/sa", NivelCargo.CANTON, 1);
    Cargo guardado = new Cargo(dto.nombre(), dto.nivel(), dto.puestos());
    guardado.setId(1L);
    when(cargoRepository.save(any(Cargo.class))).thenReturn(guardado);

    Cargo resultado = service.createCargo(dto, 99L, null);

    assertThat(resultado.getId()).isEqualTo(1L);
    verify(auditLogService)
        .registrar(eq(99L), eq("CREAR_CARGO"), eq("cargo"), eq(1L), any(), any());
  }

  @Test
  void updateCargo_whenNotFound_throws() {
    when(cargoRepository.findById(1L)).thenReturn(Optional.empty());
    CargoRequest dto = new CargoRequest("Alcalde/sa", NivelCargo.CANTON, 1);

    assertThatThrownBy(() -> service.updateCargo(1L, dto, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class);
  }

  @Test
  void createPartido_withDuplicateNumeroLista_throwsConflict() {
    Partido existente = new Partido("Alianza PAIS", "AP", 35);
    existente.setId(2L);
    when(partidoRepository.findByNumeroLista(35)).thenReturn(Optional.of(existente));
    PartidoRequest dto = new PartidoRequest("Otro Partido", "OP", 35);

    assertThatThrownBy(() -> service.createPartido(dto, 99L, null))
        .isInstanceOf(ConfiguracionConflictException.class);

    verify(partidoRepository, never()).save(any());
  }

  @Test
  void createPartido_savesAndAudits() {
    when(partidoRepository.findByNumeroLista(6)).thenReturn(Optional.empty());
    PartidoRequest dto = new PartidoRequest("PSC", "PSC", 6);
    Partido guardado = new Partido(dto.nombre(), dto.siglas(), dto.numeroLista());
    guardado.setId(3L);
    when(partidoRepository.save(any(Partido.class))).thenReturn(guardado);

    Partido resultado = service.createPartido(dto, 99L, null);

    assertThat(resultado.getId()).isEqualTo(3L);
    verify(auditLogService)
        .registrar(eq(99L), eq("CREAR_PARTIDO"), eq("partido"), eq(3L), any(), any());
  }

  @Test
  void createCandidato_whenCargoNotFound_throws() {
    when(cargoRepository.findById(10L)).thenReturn(Optional.empty());
    CandidatoRequest dto = new CandidatoRequest("Juan Perez", 1, 10L, 20L);

    assertThatThrownBy(() -> service.createCandidato(dto, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class);
  }

  @Test
  void createCandidato_whenPartidoNotFound_throws() {
    Cargo cargo = new Cargo("Alcalde/sa", NivelCargo.CANTON, 1);
    cargo.setId(10L);
    when(cargoRepository.findById(10L)).thenReturn(Optional.of(cargo));
    when(partidoRepository.findById(20L)).thenReturn(Optional.empty());
    CandidatoRequest dto = new CandidatoRequest("Juan Perez", 1, 10L, 20L);

    assertThatThrownBy(() -> service.createCandidato(dto, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class);
  }

  @Test
  void createCandidato_savesAndAudits() {
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

    Candidato resultado = service.createCandidato(dto, 99L, null);

    assertThat(resultado.getId()).isEqualTo(1L);
    verify(auditLogService)
        .registrar(eq(99L), eq("CREAR_CANDIDATO"), eq("candidato"), eq(1L), any(), any());
  }
}
