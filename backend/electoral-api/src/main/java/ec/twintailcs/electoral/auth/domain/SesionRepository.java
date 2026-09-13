package ec.twintailcs.electoral.auth.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionRepository extends JpaRepository<Sesion, Long> {

  Optional<Sesion> findBySesionId(String sesionId);

  Optional<Sesion> findByRefreshHash(String refreshHash);

  Page<Sesion> findByRevocadoFalseAndExpiraEnAfter(Instant now, Pageable pageable);

  Page<Sesion> findByUsuarioIdAndRevocadoFalseAndExpiraEnAfter(
      Long usuarioId, Instant now, Pageable pageable);

  Page<Sesion> findByRevocadoFalse(Pageable pageable);
}
