package ec.twintailcs.electoral.acta.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;

@Entity
@Table(name = "acta")
public class Acta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable = false) private Long version;
    @UuidGenerator(algorithm = UuidV7Generator.class)
    @Column(name = "public_id", unique = true, nullable = false, updatable = false) private UUID publicId;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "jrv_id", nullable = false) private Jrv jrv;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "cargo_id", nullable = false) private Cargo cargo;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "registrada_por", nullable = false) private Usuario registradaPor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoActa estado = EstadoActa.BORRADOR;
    @Column(name = "storage_url", length = 512) private String storageUrl;
    @Column(name = "total_sufragantes", nullable = false) private Integer totalSufragantes;
    @Column(name = "votos_blancos", nullable = false) private Integer votosBlancos;
    @Column(name = "votos_nulos", nullable = false) private Integer votosNulos;
    @CreationTimestamp @Column(name = "fecha_registro", nullable = false, updatable = false) private Instant fechaRegistro;
    @Column(name = "motivo_anulacion", length = 500) private String motivoAnulacion;
    public Acta() {}
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getVersion(){return version;} public UUID getPublicId(){return publicId;} public void setPublicId(UUID v){publicId=v;}
    public Jrv getJrv(){return jrv;} public void setJrv(Jrv v){jrv=v;}
    public Cargo getCargo(){return cargo;} public void setCargo(Cargo v){cargo=v;}
    public Usuario getRegistradaPor(){return registradaPor;} public void setRegistradaPor(Usuario v){registradaPor=v;}
    public EstadoActa getEstado(){return estado;} public void setEstado(EstadoActa v){estado=v;}
    public String getStorageUrl(){return storageUrl;} public void setStorageUrl(String v){storageUrl=v;}
    public Integer getTotalSufragantes(){return totalSufragantes;} public void setTotalSufragantes(Integer v){totalSufragantes=v;}
    public Integer getVotosBlancos(){return votosBlancos;} public void setVotosBlancos(Integer v){votosBlancos=v;}
    public Integer getVotosNulos(){return votosNulos;} public void setVotosNulos(Integer v){votosNulos=v;}
    public Instant getFechaRegistro(){return fechaRegistro;}
    public String getMotivoAnulacion(){return motivoAnulacion;} public void setMotivoAnulacion(String v){motivoAnulacion=v;}
}
