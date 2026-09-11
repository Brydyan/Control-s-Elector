package ec.twintailcs.electoral.auth.domain;

import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Tabla intermedia: muchos-a-muchos entre roles y permisos.
 *
 * <p>{@code rol} es ahora FK a la entidad {@link Rol}.
 */
@Entity
@Table(
    name = "rol_permiso",
    indexes = {
      @Index(name = "ix_rol_permiso_permiso", columnList = "permiso_id"),
      @Index(name = "ix_rol_permiso_rol_id", columnList = "rol_id")
    })
public class RolPermiso {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @UuidGenerator(algorithm = UuidV7Generator.class)
  @Column(name = "public_id", nullable = false, unique = true, updatable = false)
  private UUID publicId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "rol_id", nullable = false)
  private Rol rol;

  /** FK a permiso. */
  @Column(name = "permiso_id", nullable = false)
  private Long permisoId;

  /**
   * @deprecated solo JPA.
   */
  @Deprecated
  protected RolPermiso() {}

  public RolPermiso(Rol rol, Long permisoId) {
    this.rol = rol;
    this.permisoId = permisoId;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public void setPublicId(UUID publicId) {
    this.publicId = publicId;
  }

  public Rol getRol() {
    return rol;
  }

  public void setRol(Rol rol) {
    this.rol = rol;
  }

  public Long getPermisoId() {
    return permisoId;
  }

  public void setPermisoId(Long permisoId) {
    this.permisoId = permisoId;
  }
}
