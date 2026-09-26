package ec.twintailcs.electoral.incidencia.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidenciaJrvRepository extends JpaRepository<IncidenciaJrv, Long> {

  /** Listado para monitoreo COORDINATOR/MASTER/ADMIN. Ordenado por fecha desc. */
  List<IncidenciaJrv> findAllByOrderByFechaRegistroDesc();

  /** Filtrado por JRV específica. */
  List<IncidenciaJrv> findByJrvIdOrderByFechaRegistroDesc(Long jrvId);
}
