package ec.twintailcs.electoral.acta.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.auth.domain.Usuario;

@Entity
@Table(name = "acta")
public class Acta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "jrv_id", nullable = false)
    private Jrv jrv;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id", nullable = false)
    private Cargo cargo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "registrada_por", nullable = false)
    private Usuario registradaPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoActa estado = EstadoActa.BORRADOR;

    @Column(name = "storage_url")
    private String storageUrl;

    @Column(name = "total_sufragantes", nullable = false)
    private Integer totalSufragantes;

    @Column(name = "votos_blancos", nullable = false)
    private Integer votosBlancos;

    @Column(name = "votos_nulos", nullable = false)
    private Integer votosNulos;

    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false)
    private Instant fechaRegistro;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    public Acta() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Jrv getJrv() { return jrv; }
    public void setJrv(Jrv jrv) { this.jrv = jrv; }

    public Cargo getCargo() { return cargo; }
    public void setCargo(Cargo cargo) { this.cargo = cargo; }

    public Usuario getRegistradaPor() { return registradaPor; }
    public void setRegistradaPor(Usuario registradaPor) { this.registradaPor = registradaPor; }

    public EstadoActa getEstado() { return estado; }
    public void setEstado(EstadoActa estado) { this.estado = estado; }

    public String getStorageUrl() { return storageUrl; }
    public void setStorageUrl(String storageUrl) { this.storageUrl = storageUrl; }

    public Integer getTotalSufragantes() { return totalSufragantes; }
    public void setTotalSufragantes(Integer totalSufragantes) { this.totalSufragantes = totalSufragantes; }

    public Integer getVotosBlancos() { return votosBlancos; }
    public void setVotosBlancos(Integer votosBlancos) { this.votosBlancos = votosBlancos; }

    public Integer getVotosNulos() { return votosNulos; }
    public void setVotosNulos(Integer votosNulos) { this.votosNulos = votosNulos; }

    public Instant getFechaRegistro() { return fechaRegistro; }

    public String getMotivoAnulacion() { return motivoAnulacion; }
    public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }
}
