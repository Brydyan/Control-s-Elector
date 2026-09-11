package ec.twintailcs.electoral.geografia.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParroquiaRepository extends JpaRepository<Parroquia, Long> {
  List<Parroquia> findByCanton(Canton canton);

  List<Parroquia> findByEsUrbana(boolean esUrbana);
}
