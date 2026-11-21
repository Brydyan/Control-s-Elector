package ec.twintailcs.electoral.geografia.domain.repository;

import ec.twintailcs.electoral.geografia.domain.model.UsuarioRecinto;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for UsuarioRecinto entity (user-to-recinto assignments).
 *
 * <p>Handles CRUD and common queries for user assignments to recintos.
 */
@ApplicationScoped
public class UsuarioRecintoRepository implements PanacheRepository<UsuarioRecinto> {

  /**
   * Find all assignments for a user in a given recinto. (Should be 0 or 1, enforced by unique
   * constraint)
   */
  public Optional<UsuarioRecinto> findByUsuarioAndRecinto(UUID usuarioId, UUID recintoId) {
    return find("usuario_id = ?1 AND recinto_id = ?2", usuarioId, recintoId).firstResultOptional();
  }

  /** Find all assignments for a user (all recintos they're assigned to). */
  public List<UsuarioRecinto> findByUsuario(UUID usuarioId) {
    return find("usuario_id = ?1 ORDER BY fecha_asignacion DESC", usuarioId).list();
  }

  /**
   * Find all assignments for a user filtered by role. Example: get all recintos where user is
   * COORDINATOR.
   */
  public List<UsuarioRecinto> findByUsuarioAndRol(UUID usuarioId, UsuarioRecinto.RolEnRecinto rol) {
    return find(
            "usuario_id = ?1 AND rol_en_recinto = ?2 ORDER BY fecha_asignacion DESC",
            usuarioId,
            rol)
        .list();
  }

  /** Find all users assigned to a recinto. */
  public List<UsuarioRecinto> findByRecinto(UUID recintoId) {
    return find("recinto_id = ?1 ORDER BY rol_en_recinto DESC, fecha_asignacion DESC", recintoId)
        .list();
  }

  /**
   * Find all users assigned to a recinto filtered by role. Example: get all observers assigned to a
   * recinto.
   */
  public List<UsuarioRecinto> findByRecintoAndRol(UUID recintoId, UsuarioRecinto.RolEnRecinto rol) {
    return find(
            "recinto_id = ?1 AND rol_en_recinto = ?2 ORDER BY fecha_asignacion DESC",
            recintoId,
            rol)
        .list();
  }

  /** Count observers assigned to a recinto. */
  public long countObserversByRecinto(UUID recintoId) {
    return count(
        "recinto_id = ?1 AND rol_en_recinto = ?2", recintoId, UsuarioRecinto.RolEnRecinto.OBSERVER);
  }

  /** Count coordinators assigned to a recinto. */
  public long countCoordinatorsByRecinto(UUID recintoId) {
    return count(
        "recinto_id = ?1 AND rol_en_recinto = ?2",
        recintoId,
        UsuarioRecinto.RolEnRecinto.COORDINATOR);
  }

  /** Check if a user is assigned to a recinto with a specific role. */
  public boolean isUserAssignedToRecintoWithRole(
      UUID usuarioId, UUID recintoId, UsuarioRecinto.RolEnRecinto rol) {
    return count(
            "usuario_id = ?1 AND recinto_id = ?2 AND rol_en_recinto = ?3",
            usuarioId,
            recintoId,
            rol)
        > 0;
  }

  /**
   * Delete all assignments for a user in a recinto with a given role. Useful for bulk updates
   * (e.g., removing all old observers before adding new ones).
   */
  public void deleteByRecintoAndRol(UUID recintoId, UsuarioRecinto.RolEnRecinto rol) {
    delete("recinto_id = ?1 AND rol_en_recinto = ?2", recintoId, rol);
  }

  /** Delete all assignments for a recinto (cascade cleanup). */
  public void deleteByRecinto(UUID recintoId) {
    delete("recinto_id = ?1", recintoId);
  }

  /**
   * Get all recinto IDs where a user is assigned as COORDINATOR. Used for RLS filtering in
   * actas-pendientes.
   */
  public Set<UUID> findRecintoIdsByCoordinator(UUID usuarioId) {
    return find(
            "SELECT recinto_id FROM usuario_recinto WHERE usuario_id = ?1 AND rol_en_recinto = ?2",
            usuarioId,
            UsuarioRecinto.RolEnRecinto.COORDINATOR)
        .project(UUID.class)
        .list()
        .stream()
        .collect(java.util.stream.Collectors.toSet());
  }

  /** Check if user has ANY coordinator assignment to ANY recinto. */
  public boolean isCoordinatorOfAnyRecinto(UUID usuarioId) {
    return count(
            "usuario_id = ?1 AND rol_en_recinto = ?2",
            usuarioId,
            UsuarioRecinto.RolEnRecinto.COORDINATOR)
        > 0;
  }
}
