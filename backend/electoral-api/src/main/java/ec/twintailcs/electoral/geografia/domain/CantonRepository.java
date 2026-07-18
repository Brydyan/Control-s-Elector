package ec.twintailcs.electoral.geografia.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CantonRepository extends JpaRepository<Canton, Long> {
    Optional<Canton> findByCodigo(String codigo);
    Optional<Canton> findByNombre(String nombre);
    List<Canton> findByProvincia(Provincia provincia);
}
