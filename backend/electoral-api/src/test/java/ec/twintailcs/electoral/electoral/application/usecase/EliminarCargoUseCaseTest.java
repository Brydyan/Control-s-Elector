package ec.twintailcs.electoral.electoral.application.usecase;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EliminarCargoUseCaseTest {

  @Mock private CargoRepository cargoRepository;
  @Mock private AuditLogService auditLogService;

  private EliminarCargoUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new EliminarCargoUseCase(cargoRepository, auditLogService);
  }

  @Test
  void execute_whenNotFound_throws() {
    when(cargoRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(1L, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class)
        .hasMessageContaining("Cargo no encontrado: 1");

    verify(cargoRepository, never()).delete(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_success_deletesAndAudits() {
    Cargo cargo = new Cargo("Alcalde", NivelCargo.CANTON, 1);
    cargo.setId(1L);
    when(cargoRepository.findById(1L)).thenReturn(Optional.of(cargo));

    useCase.execute(1L, 99L, null);

    verify(cargoRepository).delete(cargo);
    verify(auditLogService)
        .registrar(
            eq(99L),
            eq("ELIMINAR_CARGO"),
            eq("cargo"),
            eq(1L),
            eq("Cargo eliminado: Alcalde"),
            eq(null));
  }
}
