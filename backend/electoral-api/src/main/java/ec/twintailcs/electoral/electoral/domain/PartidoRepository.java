package ec.twintailcs.electoral.electoral.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartidoRepository extends JpaRepository<Partido, Long> {
    Optional<Partido> findByNombre(String nombre);
    Optional<Partido> findByNumeroLista(Integer numeroLista);
    Optional<Partido> findBySiglas(String siglas);
}
