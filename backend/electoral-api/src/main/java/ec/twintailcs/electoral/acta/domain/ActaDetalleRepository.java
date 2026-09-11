package ec.twintailcs.electoral.acta.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActaDetalleRepository extends JpaRepository<ActaDetalle, Long> {
  List<ActaDetalle> findByActaId(Long actaId);

  List<ActaDetalle> findByCandidatoId(Long candidatoId);

  List<ActaDetalle> findByActa(Acta acta);

  void deleteByActa(Acta acta);

  @Query("SELECT COALESCE(SUM(d.votos), 0) FROM ActaDetalle d WHERE d.acta = :acta")
  Integer sumarVotosByActa(@Param("acta") Acta acta);
}
