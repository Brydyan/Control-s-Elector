package ec.twintailcs.electoral.acta.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.JrvCargo;
import ec.twintailcs.electoral.electoral.domain.JrvCargoRepository;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.JrvRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuardarBorradorActaUseCaseTest {

  @Mock private ActaRepository actaRepository;
  @Mock private ActaDetalleRepository actaDetalleRepository;
  @Mock private JrvRepository jrvRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private CandidatoRepository candidatoRepository;
  @Mock private CargoRepository cargoRepository;
  @Mock private JrvCargoRepository jrvCargoRepository;
  @Mock private AuditLogService auditLogService;

  private GuardarBorradorActaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new GuardarBorradorActaUseCase(
            actaRepository,
            actaDetalleRepository,
            jrvRepository,
            usuarioRepository,
            candidatoRepository,
            cargoRepository,
            jrvCargoRepository,
            auditLogService);
  }

  @Test
  void execute_whenJrvNotFound_throws() {
    when(jrvRepository.findById(1L)).thenReturn(Optional.empty());

    ActaRequestDto dto = new ActaRequestDto(1L, 2L, 50, 0, 0, List.of());

    assertThatThrownBy(() -> useCase.execute(dto, 99L, "127.0.0.1"))
        .isInstanceOf(ActaInvalidaException.class);
  }

  @Test
  void execute_success_creaBorradorAndAudits() {
    Jrv jrv = new Jrv();
    jrv.setId(1L);
    Cargo cargo = new Cargo();
    cargo.setId(2L);
    Usuario observer = new Usuario();
    observer.setId(99L);
    JrvCargo jrvCargo = new JrvCargo(jrv, cargo);
    jrvCargo.setActivo(true);

    Candidato c1 = new Candidato();
    c1.setId(101L);

    ActaDetalleDto detalleDto = new ActaDetalleDto(101L, 20);
    ActaRequestDto dto = new ActaRequestDto(1L, 2L, 50, 0, 0, List.of(detalleDto));

    when(jrvRepository.findById(1L)).thenReturn(Optional.of(jrv));
    when(cargoRepository.findById(2L)).thenReturn(Optional.of(cargo));
    when(usuarioRepository.findById(99L)).thenReturn(Optional.of(observer));
    when(jrvCargoRepository.findByJrvIdAndCargoId(1L, 2L)).thenReturn(List.of(jrvCargo));
    when(jrvCargoRepository.findByJrvIdAndActivo(1L, true)).thenReturn(List.of(jrvCargo));
    when(actaRepository.findActaActivaBy(1L, 2L, 99L)).thenReturn(Optional.empty());
    when(candidatoRepository.findById(101L)).thenReturn(Optional.of(c1));

    when(actaRepository.save(any(Acta.class)))
        .thenAnswer(
            i -> {
              Acta a = i.getArgument(0);
              a.setId(500L);
              return a;
            });

    Acta resultado = useCase.execute(dto, 99L, "127.0.0.1");

    assertThat(resultado.getId()).isEqualTo(500L);
    assertThat(resultado.getEstado()).isEqualTo(EstadoActa.BORRADOR);
    verify(auditLogService)
        .registrarConIp(
            eq(99L),
            eq("BORRADOR_CREADO"),
            eq("acta"),
            eq(500L),
            eq("Borrador guardado: JRV=1, cargo=2"),
            eq("127.0.0.1"));
  }
}
