package ec.twintailcs.electoral.acta.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.geografia.domain.Jrv;

@Entity
@Table(name = "observer_jrv_asignacion", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"usuario_id", "jrv_id"})
})
public class ObserverJrvAsignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario observer;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "jrv_id", nullable = false)
    private Jrv jrv;

    @CreationTimestamp
    @Column(name = "asignado_en", updatable = false)
    private Instant asignadoEn;

    public ObserverJrvAsignacion() {}

    public ObserverJrvAsignacion(Usuario observer, Jrv jrv) {
        this.observer = observer;
        this.jrv = jrv;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getObserver() { return observer; }
    public void setObserver(Usuario observer) { this.observer = observer; }

    public Jrv getJrv() { return jrv; }
    public void setJrv(Jrv jrv) { this.jrv = jrv; }

    public Instant getAsignadoEn() { return asignadoEn; }
}
