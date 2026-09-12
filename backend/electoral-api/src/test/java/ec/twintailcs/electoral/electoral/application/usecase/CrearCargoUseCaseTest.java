package ec.twintailcs.electoral.electoral.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrearCargoUseCaseTest {

  @Mock private CargoRepository cargoRepository;
  @Mock private AuditLogService auditLogService;

  private CrearCargoUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CrearCargoUseCase(cargoRepository, auditLogService);
  }

  @Test
  void execute_savesAndAudits() {
    CargoRequest dto = new CargoRequest("Alcalde/sa", NivelCargo.CANTON, 1);
    Cargo guardado = new Cargo(dto.nombre(), dto.nivel(), dto.puestos());
    guardado.setId(1L);
    when(cargoRepository.save(any(Cargo.class))).thenReturn(guardado);

    Cargo resultado = useCase.execute(dto, 99L, null);

    assertThat(resultado).isNotNull();
    assertThat(resultado.getId()).isEqualTo(1L);
    assertThat(resultado.getNombre()).isEqualTo("Alcalde/sa");
    assertThat(resultado.getNivel()).isEqualTo(NivelCargo.CANTON);
    assertThat(resultado.getPuestos()).isEqualTo(1);

    verify(cargoRepository).save(any(Cargo.class));
    verify(auditLogService)
        .registrarConIp(
            eq(99L),
            eq("CREAR_CARGO"),
            eq("cargo"),
            eq(1L),
            eq("Cargo creado: Alcalde/sa"),
            eq(null));
  }
}
