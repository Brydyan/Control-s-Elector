package ec.twintailcs.electoral.geografia.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JrvRepository extends JpaRepository<Jrv, Long> {
    Optional<Jrv> findByNumero(String numero);
    List<Jrv> findByParroquia(Parroquia parroquia);
}
