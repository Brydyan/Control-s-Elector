package ec.twintailcs.electoral.notificacion.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.acta.domain.Acta;

@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "destinatario_id", nullable = false)
    private Usuario destinatario;

    @Column(nullable = false, length = 100)
    private String tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acta_id")
    private Acta acta;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    @Column(nullable = false)
    private Boolean leida = false;

    @CreationTimestamp
    @Column(name = "creada_en", updatable = false)
    private Instant creadaEn;

    public Notificacion() {}

    public Notificacion(Usuario destinatario, String tipo, Acta acta, String mensaje) {
        this.destinatario = destinatario;
        this.tipo = tipo;
        this.acta = acta;
        this.mensaje = mensaje;
        this.leida = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getDestinatario() { return destinatario; }
    public void setDestinatario(Usuario destinatario) { this.destinatario = destinatario; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Acta getActa() { return acta; }
    public void setActa(Acta acta) { this.acta = acta; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public Boolean getLeida() { return leida; }
    public void setLeida(Boolean leida) { this.leida = leida; }

    public Instant getCreadaEn() { return creadaEn; }
}
