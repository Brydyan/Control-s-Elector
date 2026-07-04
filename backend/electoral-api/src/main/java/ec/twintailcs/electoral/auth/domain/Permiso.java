package ec.twintailcs.electoral.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Permiso granular del sistema.
 *
 * Cada permiso representa la capacidad de ejecutar una accion sobre un
 * recurso. Se asignan a roles via {@code RolPermiso}.
 *
 * Identificacion: la clave canonica es "{recurso}:{accion}"
 * (ej: "evidencia:upload", "actas:aprobar"). Se usa como authority string
 * en Spring Security y vive en el claim del JWT.
 *
 * Naming convention de recursos: plural en snake_case, lowercase.
 *   evidencia, acta, jrv, usuario, auth
 *
 * Naming convention de acciones: lowercase.
 *   read, create, update, delete, approve, reject, download
 */
@Entity
@Table(name = "permiso", uniqueConstraints = {
        @UniqueConstraint(name = "uq_permiso_recurso_accion",
                columnNames = {"recurso", "accion"})
}, indexes = {
        @Index(name = "ix_permiso_recurso_accion", columnList = "recurso,accion")
})
public class Permiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Recurso protegido. Obligatorio. ej: "evidencia", "acta" */
    @Column(nullable = false, length = 64)
    private String recurso;

    /** Accion permitida sobre el recurso. Obligatorio. ej: "upload" */
    @Column(nullable = false, length = 64)
    private String accion;

    /** Descripcion human-readable. Opcional, nullable. */
    @Column(length = 256)
    private String descripcion;

    public Permiso() {}

    public Permiso(String recurso, String accion, String descripcion) {
        this.recurso = recurso;
        this.accion = accion;
        this.descripcion = descripcion;
    }

    /** Authority string que Spring Security y el JWT entienden: "evidencia:upload". */
    public String authority() {
        return recurso + ":" + accion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRecurso() { return recurso; }
    public void setRecurso(String recurso) { this.recurso = recurso; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
