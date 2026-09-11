package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarCandidatosUseCase {

  private final CandidatoRepository candidatoRepository;

  public ListarCandidatosUseCase(CandidatoRepository candidatoRepository) {
    this.candidatoRepository = candidatoRepository;
  }

  @Transactional(readOnly = true)
  public List<Candidato> execute(Long cargoId, Long partidoId) {
    if (cargoId != null && partidoId != null) {
      return candidatoRepository.findByCargoIdAndPartidoId(cargoId, partidoId);
    }
    if (cargoId != null) {
      return candidatoRepository.findByCargoId(cargoId);
    }
    if (partidoId != null) {
      return candidatoRepository.findByPartidoId(partidoId);
    }
    return candidatoRepository.findAll();
  }
}
