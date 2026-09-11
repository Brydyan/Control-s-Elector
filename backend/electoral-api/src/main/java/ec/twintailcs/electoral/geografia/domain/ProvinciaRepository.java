package ec.twintailcs.electoral.geografia.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinciaRepository extends JpaRepository<Provincia, Long> {
  Optional<Provincia> findByCodigo(String codigo);

  Optional<Provincia> findByNombre(String nombre);
}
