package ec.twintailcs.electoral.auth.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;

/**
 * Tabla intermedia: muchos-a-muchos entre roles y permisos.
 *
 * El "nombre" del rol vive como String en esta tabla (no como FK a una
 * tabla de roles) para mantener el modelo minimalista del proyecto:
 * los nombres de rol se siembran en {@code DataSeeder}. Cuando tengamos
 * gestion de roles via API, se promueve nombre a FK.
 *
 * Llave compuesta: (rol, permisoId).
 */
@Entity
@Table(name = "rol_permiso", indexes = {
        @Index(name = "ix_rol_permiso_rol", columnList = "rol"),
        @Index(name = "ix_rol_permiso_permiso", columnList = "permiso_id")
})
public class RolPermiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @UuidGenerator(algorithm = UuidV7Generator.class)
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    /** Nombre del rol (ej: "OBSERVER", "COORDINATOR"). Unico + not null. */
    @Column(nullable = false, length = 32)
    private String rol;

    /** FK a permiso. */
    @Column(name = "permiso_id", nullable = false)
    private Long permisoId;

    public RolPermiso() {}

    public RolPermiso(String rol, Long permisoId) {
        this.rol = rol;
        this.permisoId = permisoId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getPublicId() { return publicId; }
    public void setPublicId(UUID publicId) { this.publicId = publicId; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public Long getPermisoId() { return permisoId; }
    public void setPermisoId(Long permisoId) { this.permisoId = permisoId; }
}
