package ec.twintailcs.electoral.geografia.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Recinto entity.
 *
 * <p>Provides query methods for finding Recintos by parroquia, coordinator, and unique constraints.
 */
@ApplicationScoped
public class RecintoRepository implements PanacheRepository<Recinto> {

  /**
   * Find Recinto by id (helper method, not overriding PanacheRepository).
   *
   * @param id the Recinto id
   * @return Optional containing the Recinto if found, empty otherwise
   */
  public Optional<Recinto> findByIdOptional(Long id) {
    return find("id", id).firstResultOptional();
  }

  /**
   * Find all Recintos assigned to a Parroquia.
   *
   * @param parroquiaId the Parroquia id
   * @return List of Recintos in that Parroquia
   */
  public List<Recinto> findByParroquiaId(Long parroquiaId) {
    return find("parroquiaId", parroquiaId).list();
  }

  /**
   * Find all Recintos managed by a Coordinator.
   *
   * @param coordinatorId the Coordinator (Usuario) id
   * @return List of Recintos assigned to that Coordinator
   */
  public List<Recinto> findByCoordinatorId(Long coordinatorId) {
    return find("coordinatorId", coordinatorId).list();
  }

  /**
   * Check if a Recinto with the given nombre + parroquiaId combination already exists.
   *
   * @param nombre the Recinto nombre
   * @param parroquiaId the Parroquia id
   * @return true if exists, false otherwise
   */
  public boolean existsByNombreAndParroquiaId(String nombre, Long parroquiaId) {
    return find("nombre = ?1 and parroquiaId = ?2", nombre, parroquiaId).firstResultOptional()
        .isPresent();
  }

  /**
   * Count how many JRVs are assigned to a Recinto.
   *
   * @param recintoId the Recinto id
   * @return count of JRVs
   */
  public long countJrvsInRecinto(Long recintoId) {
    return this.getEntityManager()
        .createQuery("select count(j) from Jrv j where j.recintoId = :recintoId", Long.class)
        .setParameter("recintoId", recintoId)
        .getSingleResult();
  }
}
