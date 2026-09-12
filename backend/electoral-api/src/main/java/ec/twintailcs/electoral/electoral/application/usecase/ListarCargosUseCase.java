package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarCargosUseCase {

  private final CargoRepository cargoRepository;

  public ListarCargosUseCase(CargoRepository cargoRepository) {
    this.cargoRepository = cargoRepository;
  }

  @Transactional(readOnly = true)
  public List<Cargo> execute() {
    return cargoRepository.findAll();
  }
}
