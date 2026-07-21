package ec.twintailcs.electoral.acta.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActaDetalleRepository extends JpaRepository<ActaDetalle, Long> {
    List<ActaDetalle> findByActaId(Long actaId);
    List<ActaDetalle> findByCandidatoId(Long candidatoId);
}
