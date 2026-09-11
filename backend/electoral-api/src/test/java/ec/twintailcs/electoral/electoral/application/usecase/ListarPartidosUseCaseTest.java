package ec.twintailcs.electoral.electoral.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListarPartidosUseCaseTest {

  @Mock private PartidoRepository partidoRepository;

  private ListarPartidosUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new ListarPartidosUseCase(partidoRepository);
  }

  @Test
  void execute_returnsAllPartidos() {
    Partido partido = new Partido("PSC", "PSC", 6);
    when(partidoRepository.findAll()).thenReturn(List.of(partido));

    List<Partido> resultado = useCase.execute();

    assertThat(resultado).containsExactly(partido);
    verify(partidoRepository).findAll();
  }
}
