package ec.twintailcs.electoral.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionRepository extends JpaRepository<Sesion, Long> {

    Optional<Sesion> findBySesionId(String sesionId);

    Optional<Sesion> findByRefreshHash(String refreshHash);
}
