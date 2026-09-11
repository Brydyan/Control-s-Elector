package ec.twintailcs.electoral.acta.domain;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Asignación observador ↔ JRV.
 *
 * <p>Cada fila declara que un OBSERVER puede registrar/consultar actas
 * para una JRV específica. La unicidad (usuario_id, jrv_id) impide
 * duplicados accidentales.
 *
 * <p>Nota histórica: la columna se llama {@code usuario_id} (no
 * {@code observer_id}) desde la consolidación del esquema en PR α.
 */
@Entity
@Table(
    name = "observer_jrv_asignacion",
    uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "jrv_id"})
)
public class ObserverJrvAsignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "jrv_id", nullable = false)
    private Jrv jrv;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    public ObserverJrvAsignacion() {
    }

    public ObserverJrvAsignacion(Usuario usuario, Jrv jrv) {
        this.usuario = usuario;
        this.jrv = jrv;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Jrv getJrv() {
        return jrv;
    }

    public void setJrv(Jrv jrv) {
        this.jrv = jrv;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}