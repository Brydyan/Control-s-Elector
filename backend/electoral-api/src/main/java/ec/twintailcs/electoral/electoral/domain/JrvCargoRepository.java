package ec.twintailcs.electoral.electoral.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JrvCargoRepository extends JpaRepository<JrvCargo, JrvCargoId> {
  List<JrvCargo> findByJrvId(Long jrvId);

  List<JrvCargo> findByCargoId(Long cargoId);

  List<JrvCargo> findByJrvIdAndCargoId(Long jrvId, Long cargoId);

  List<JrvCargo> findByJrvIdAndActivo(Long jrvId, boolean activo);
}
