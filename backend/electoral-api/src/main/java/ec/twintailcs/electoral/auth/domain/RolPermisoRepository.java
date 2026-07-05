package ec.twintailcs.electoral.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RolPermisoRepository extends JpaRepository<RolPermiso, Long> {

    Optional<RolPermiso> findByPublicId(UUID publicId);

    /** Lista de permisos asignados al rol (devuelve el objeto completo). */
    @Query("SELECT rp FROM RolPermiso rp WHERE rp.rol.nombre = :rolNombre")
    List<RolPermiso> findByRolNombre(String rolNombre);

    /**
     * Authority strings del usuario via JOIN: Usuario.rol -> RolPermiso -> Permiso.
     * Hibernate 7 (Spring Boot 4) rechaza string concat en JPQL, uso
     * CONCAT(...) con sus overloads posicionales.
     *
     * La comparacion {@code u.rol = rp.rol} funciona entre entidades:
     * JPQL traduce a {@code u.rol_id = rp.rol_id} internamente.
     */
    @Query("""
            SELECT CONCAT(p.recurso, ':', p.accion)
            FROM Permiso p, RolPermiso rp, Usuario u
            WHERE rp.permisoId = p.id
              AND u.id = :usuarioId
              AND u.rol = rp.rol
            """)
    List<String> findAuthorityStringsByUsuarioId(Long usuarioId);

    /** Idem por nombre de rol directo (para seeders y tests). */
    @Query("""
            SELECT CONCAT(p.recurso, ':', p.accion)
            FROM Permiso p, RolPermiso rp
            WHERE rp.permisoId = p.id AND rp.rol.nombre = :rol
            """)
    List<String> findAuthorityStringsByRol(String rol);
}
