package ec.twintailcs.electoral.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de menus.
 *
 * <p>Estrategia: traer el catalogo completo (esperado ~20-50 filas) y filtrar en el MenuService.
 * Las cardinalidades son chicas y un JOIN con N queries parametrizadas seria over-engineering.
 *
 * <p>Las queries dejan los permisos fuera del fetch principal porque MenuPermiso no
 * modela @ManyToOne hacia Permiso (por consistencia con RolPermiso del codebase). Se resuelve con
 * un lookup en MenuPermisoRepository + PermisoRepository desde el servicio.
 */
public interface MenuRepository extends JpaRepository<Menu, Long> {

  /**
   * Catalogo completo de menus activos, ordenados por (orden, label). Excluye borradoEn != null.
   */
  @Query(
      """
            SELECT m FROM Menu m
            WHERE m.borradoEn IS NULL
              AND m.activo = TRUE
            ORDER BY m.orden ASC, m.label ASC
            """)
  List<Menu> findAllActive();

  /**
   * Hijos directos de un menu padre, ordenados. Usado por el walk-up para revalidar visibilidad
   * cuando un padre se agrega manualmente.
   */
  @Query(
      """
            SELECT m FROM Menu m
            WHERE m.parentId = :parentId
              AND m.borradoEn IS NULL
              AND m.activo = TRUE
            ORDER BY m.orden ASC, m.label ASC
            """)
  List<Menu> findActiveChildrenOf(@Param("parentId") Long parentId);

  Optional<Menu> findByPublicId(UUID publicId);
}
