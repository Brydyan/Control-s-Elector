package ec.twintailcs.electoral.geografia.application;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.domain.Recinto;
import ec.twintailcs.electoral.geografia.domain.RecintoRepository;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Service for Recinto CRUD operations.
 *
 * <p>Validates: - Parroquia exists - Coordinator exists and has COORDINATOR role - Nombre +
 * Parroquia are unique - Cannot delete Recinto with assigned JRVs
 */
@ApplicationScoped
public class RecintoService {

  @Inject RecintoRepository repository;

  @Inject EntityManager entityManager;

  /**
   * Create a new Recinto.
   *
   * @param nombre Recinto name (must be unique per Parroquia)
   * @param parroquiaId Parent Parroquia id
   * @param coordinatorId Coordinator (Usuario) id with COORDINATOR role
   * @param estado Initial state (usually "ACTIVO")
   * @return Created Recinto
   * @throws GeografiaNotFoundException if Parroquia or Coordinator not found
   * @throws GeografiaConflictException if nombre+parroquia already exists or Coordinator invalid
   */
  @Transactional
  public Recinto crearRecinto(String nombre, Long parroquiaId, Long coordinatorId, String estado) {
    // Validate Parroquia exists
    Parroquia parroquia = Parroquia.findById(parroquiaId);
    if (parroquia == null) {
      throw new GeografiaNotFoundException("Parroquia " + parroquiaId + " not found");
    }

    // Check unique constraint: nombre + parroquiaId
    if (repository.existsByNombreAndParroquiaId(nombre, parroquiaId)) {
      throw new GeografiaConflictException(
          "Recinto with nombre=" + nombre + " already exists in Parroquia " + parroquiaId);
    }

    // Validate Coordinator if provided
    if (coordinatorId != null) {
      Usuario coordinator = Usuario.findById(coordinatorId);
      if (coordinator == null) {
        throw new GeografiaNotFoundException("Coordinator " + coordinatorId + " not found");
      }
      // Note: Role validation is deferred until role enum is available in auth module
    }

    // Create and persist
    Recinto recinto = new Recinto(nombre, parroquiaId, coordinatorId, estado);
    repository.persist(recinto);
    return recinto;
  }

  /**
   * Update an existing Recinto.
   *
   * @param id Recinto id
   * @param nombre New nombre (optional)
   * @param coordinatorId New Coordinator id (optional)
   * @param estado New estado (optional)
   * @return Updated Recinto
   * @throws GeografiaNotFoundException if Recinto not found
   * @throws GeografiaConflictException if new nombre violates unique constraint or Coordinator
   *     invalid
   */
  @Transactional
  public Recinto actualizarRecinto(Long id, String nombre, Long coordinatorId, String estado) {
    Recinto recinto =
        repository
            .findByIdOptional(id)
            .orElseThrow(() -> new GeografiaNotFoundException("Recinto " + id + " not found"));

    // Validate new nombre if provided (must be unique per Parroquia)
    if (nombre != null && !nombre.equals(recinto.getNombre())) {
      if (repository.existsByNombreAndParroquiaId(nombre, recinto.getParroquiaId())) {
        throw new GeografiaConflictException(
            "Recinto with nombre="
                + nombre
                + " already exists in Parroquia "
                + recinto.getParroquiaId());
      }
      recinto.setNombre(nombre);
    }

    // Validate new Coordinator if provided
    if (coordinatorId != null && !coordinatorId.equals(recinto.getCoordinatorId())) {
      Usuario coordinator = Usuario.findById(coordinatorId);
      if (coordinator == null) {
        throw new GeografiaNotFoundException("Coordinator " + coordinatorId + " not found");
      }
      recinto.setCoordinatorId(coordinatorId);
    }

    // Update estado if provided
    if (estado != null && !estado.equals(recinto.getEstado())) {
      recinto.setEstado(estado);
    }

    repository.persist(recinto);
    return recinto;
  }

  /**
   * Get a Recinto by id.
   *
   * @param id Recinto id
   * @return Recinto if found
   * @throws GeografiaNotFoundException if not found
   */
  @Transactional
  public Recinto obtenerRecinto(Long id) {
    return repository
        .findByIdOptional(id)
        .orElseThrow(() -> new GeografiaNotFoundException("Recinto " + id + " not found"));
  }

  /**
   * List all Recintos with pagination.
   *
   * @param page Page number (0-indexed)
   * @param size Page size
   * @return Paged list of Recintos
   */
  @Transactional
  public PagedResponse<Recinto> listarRecintos(int page, int size) {
    long total = repository.count();
    List<Recinto> data =
        repository
            .findAll(io.quarkus.panache.common.Sort.by("id"))
            .page(io.quarkus.panache.common.Page.of(page, size))
            .list();
    return PagedResponse.of(data, page, size, total);
  }

  /**
   * Delete a Recinto.
   *
   * @param id Recinto id
   * @throws GeografiaNotFoundException if not found
   * @throws GeografiaConflictException if Recinto has JRVs assigned
   */
  @Transactional
  public void eliminarRecinto(Long id) {
    Recinto recinto =
        repository
            .findByIdOptional(id)
            .orElseThrow(() -> new GeografiaNotFoundException("Recinto " + id + " not found"));

    // Check if Recinto has JRVs assigned
    long jrvCount = repository.countJrvsInRecinto(id);
    if (jrvCount > 0) {
      throw new GeografiaConflictException(
          "Cannot delete Recinto " + id + " with " + jrvCount + " assigned JRVs");
    }

    repository.deleteById(recinto.getId());
  }

  /**
   * Bulk assign JRVs to a Recinto.
   *
   * @param recintoId Recinto id
   * @param jrvIds List of JRV ids to assign
   * @throws GeografiaNotFoundException if Recinto not found
   */
  @Transactional
  public void asignarJrvsAlRecinto(Long recintoId, List<Long> jrvIds) {
    // Validate Recinto exists
    Recinto recinto =
        repository
            .findByIdOptional(recintoId)
            .orElseThrow(
                () -> new GeografiaNotFoundException("Recinto " + recintoId + " not found"));

    if (jrvIds == null || jrvIds.isEmpty()) {
      return;
    }

    // Bulk update JRVs
    entityManager
        .createQuery("update Jrv j set j.recintoId = :recintoId where j.id in :jrvIds")
        .setParameter("recintoId", recintoId)
        .setParameter("jrvIds", jrvIds)
        .executeUpdate();
  }
}
