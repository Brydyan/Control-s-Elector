package ec.twintailcs.electoral.electoral.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListarCandidatosUseCaseTest {

  @Mock private CandidatoRepository candidatoRepository;

  private ListarCandidatosUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new ListarCandidatosUseCase(candidatoRepository);
  }

  @Test
  void execute_withCargoIdAndPartidoId_callsFindByCargoIdAndPartidoId() {
    Candidato c = new Candidato("Candidato 1", 1, null, null);
    when(candidatoRepository.findByCargoIdAndPartidoId(10L, 20L)).thenReturn(List.of(c));

    List<Candidato> result = useCase.execute(10L, 20L);

    assertThat(result).containsExactly(c);
    verify(candidatoRepository).findByCargoIdAndPartidoId(10L, 20L);
  }

  @Test
  void execute_withOnlyCargoId_callsFindByCargoId() {
    Candidato c = new Candidato("Candidato 1", 1, null, null);
    when(candidatoRepository.findByCargoId(10L)).thenReturn(List.of(c));

    List<Candidato> result = useCase.execute(10L, null);

    assertThat(result).containsExactly(c);
    verify(candidatoRepository).findByCargoId(10L);
  }

  @Test
  void execute_withOnlyPartidoId_callsFindByPartidoId() {
    Candidato c = new Candidato("Candidato 1", 1, null, null);
    when(candidatoRepository.findByPartidoId(20L)).thenReturn(List.of(c));

    List<Candidato> result = useCase.execute(null, 20L);

    assertThat(result).containsExactly(c);
    verify(candidatoRepository).findByPartidoId(20L);
  }

  @Test
  void execute_withoutFilters_callsFindAll() {
    Candidato c = new Candidato("Candidato 1", 1, null, null);
    when(candidatoRepository.findAll()).thenReturn(List.of(c));

    List<Candidato> result = useCase.execute(null, null);

    assertThat(result).containsExactly(c);
    verify(candidatoRepository).findAll();
  }
}
