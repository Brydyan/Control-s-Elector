package ec.twintailcs.electoral.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {

  /** Busca por la clave canonica. */
  @Query("SELECT p FROM Permiso p WHERE p.recurso = :recurso AND p.accion = :accion")
  Optional<Permiso> findByRecursoAndAccion(String recurso, String accion);

  Optional<Permiso> findByPublicId(UUID publicId);
}
