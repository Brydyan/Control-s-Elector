package ec.twintailcs.electoral.evidencia.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservacionEvidenciaRepository
    extends JpaRepository<ObservacionEvidencia, Long> {

  List<ObservacionEvidencia> findByEvidenciaId(Long evidenciaId);

  long countByEvidenciaId(Long evidenciaId);

  void deleteByEvidenciaId(Long evidenciaId);
}
