package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarPartidosUseCase {

  private final PartidoRepository partidoRepository;

  public ListarPartidosUseCase(PartidoRepository partidoRepository) {
    this.partidoRepository = partidoRepository;
  }

  @Transactional(readOnly = true)
  public List<Partido> execute() {
    return partidoRepository.findAll();
  }
}
