package ec.twintailcs.electoral.electoral.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidatoRepository extends JpaRepository<Candidato, Long> {
    List<Candidato> findByCargoId(Long cargoId);
    List<Candidato> findByPartidoId(Long partidoId);
    List<Candidato> findByCargoIdAndPartidoId(Long cargoId, Long partidoId);
}
