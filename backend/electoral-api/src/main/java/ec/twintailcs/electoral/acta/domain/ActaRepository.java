package ec.twintailcs.electoral.acta.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActaRepository extends JpaRepository<Acta, Long> {
    Optional<Acta> findByJrvIdAndCargoId(Long jrvId, Long cargoId);
    List<Acta> findByJrvId(Long jrvId);
    List<Acta> findByEstado(EstadoActa estado);
    List<Acta> findByRegistradaPorId(Long usuarioId);
}
