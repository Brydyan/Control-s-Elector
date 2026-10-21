package ec.twintailcs.electoral.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

/** Permiso asignado a un rol. */
@Entity
@Table(name = "rol_permiso")
public class RolPermiso extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "UUID")
  UUID publicId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "rol_id", nullable = false)
  Rol rol;

  @Column(name = "permiso_id", nullable = false)
  Long permisoId;

  /**
   * Permisos (en formato {@code recurso:accion}) de un usuario, derivados de su rol.
   *
   * <p>Dos consultas en vez de un join con ON: la relacion permiso_id es una columna
   * suelta y no una asociacion, y la forma de join por entidad de Hibernate 6 con una
   * condicion que no es una relacion es justo lo que hay que evitar.
   */
  public static List<String> authorityStringsByUsuarioId(Long usuarioId) {
    Long rolId =
        getEntityManager()
            .createQuery("select u.rol.id from Usuario u where u.id = :usuarioId", Long.class)
            .setParameter("usuarioId", usuarioId)
            .getResultStream()
            .findFirst()
            .orElse(null);
    if (rolId == null) {
      return List.of();
    }
    List<Long> permisoIds =
        getEntityManager()
            .createQuery(
                "select rp.permisoId from RolPermiso rp where rp.rol.id = :rolId", Long.class)
            .setParameter("rolId", rolId)
            .getResultList();
    if (permisoIds.isEmpty()) {
      return List.of();
    }
    return getEntityManager()
        .createQuery(
            "select concat(p.recurso, ':', p.accion) from Permiso p where p.id in :ids", String.class)
        .setParameter("ids", permisoIds)
        .getResultList();
  }
}
