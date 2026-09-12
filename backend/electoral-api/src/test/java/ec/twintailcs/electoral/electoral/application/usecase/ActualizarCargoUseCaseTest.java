package ec.twintailcs.electoral.electoral.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActualizarCargoUseCaseTest {

  @Mock private CargoRepository cargoRepository;
  @Mock private AuditLogService auditLogService;

  private ActualizarCargoUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new ActualizarCargoUseCase(cargoRepository, auditLogService);
  }

  @Test
  void execute_whenNotFound_throws() {
    when(cargoRepository.findById(1L)).thenReturn(Optional.empty());
    CargoRequest dto = new CargoRequest("Alcalde/sa", NivelCargo.CANTON, 1);

    assertThatThrownBy(() -> useCase.execute(1L, dto, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class)
        .hasMessageContaining("Cargo no encontrado: 1");

    verify(cargoRepository, never()).save(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_success_updatesAndAudits() {
    Cargo existente = new Cargo("Alcalde", NivelCargo.CANTON, 1);
    existente.setId(1L);
    when(cargoRepository.findById(1L)).thenReturn(Optional.of(existente));
    when(cargoRepository.save(any(Cargo.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CargoRequest dto = new CargoRequest("Prefecto/a", NivelCargo.PROVINCIA, 2);
    Cargo resultado = useCase.execute(1L, dto, 99L, null);

    assertThat(resultado).isNotNull();
    assertThat(resultado.getId()).isEqualTo(1L);
    assertThat(resultado.getNombre()).isEqualTo("Prefecto/a");
    assertThat(resultado.getNivel()).isEqualTo(NivelCargo.PROVINCIA);
    assertThat(resultado.getPuestos()).isEqualTo(2);

    verify(cargoRepository).save(existente);
    verify(auditLogService)
        .registrar(
            eq(99L),
            eq("ACTUALIZAR_CARGO"),
            eq("cargo"),
            eq(1L),
            eq("Cargo actualizado: Prefecto/a"),
            eq(null));
  }
}
