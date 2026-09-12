package ec.twintailcs.electoral.electoral.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListarCargosUseCaseTest {

  @Mock private CargoRepository cargoRepository;

  private ListarCargosUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new ListarCargosUseCase(cargoRepository);
  }

  @Test
  void execute_returnsAllCargos() {
    Cargo cargo = new Cargo("Alcalde", NivelCargo.CANTON, 1);
    when(cargoRepository.findAll()).thenReturn(List.of(cargo));

    List<Cargo> resultado = useCase.execute();

    assertThat(resultado).containsExactly(cargo);
    verify(cargoRepository).findAll();
  }
}
