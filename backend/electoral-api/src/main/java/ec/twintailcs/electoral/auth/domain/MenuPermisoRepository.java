package ec.twintailcs.electoral.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repositorio de la tabla intermedia menu_permiso.
 *
 * Como MenuPermiso no declara @ManyToOne hacia Permiso (consistencia con
 * RolPermiso), los joins se arman manualmente en JPQL con Permiso p
 * ON p.id = mp.permisoId.
 */
public interface MenuPermisoRepository extends JpaRepository<MenuPermiso, Long> {

    /**
     * Devuelve pares (menu_id, authority_string) para todos los
     * menu_permiso activos. La conversion recurso:accion se hace en la
     * query para que el servicio pueda comparar contra los authority
     * strings del usuario sin lookup adicional.
     *
     * Hibernate 7 (Spring Boot 4) rechaza string concat en JPQL; se usa
     * CONCAT(...) con overloads posicionales (mismo patron que
     * RolPermisoRepository.findAuthorityStringsByUsuarioId).
     */
    @Query("""
            SELECT mp.menuId, CONCAT(p.recurso, ':', p.accion)
            FROM MenuPermiso mp, Permiso p
            WHERE mp.permisoId = p.id
              AND mp.borradoEn IS NULL
            """)
    List<Object[]> findAllMenuAuthorityPairs();

    Optional<MenuPermiso> findByPublicId(UUID publicId);
}