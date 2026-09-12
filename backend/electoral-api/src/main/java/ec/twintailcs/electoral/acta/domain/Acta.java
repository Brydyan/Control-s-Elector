package ec.twintailcs.electoral.acta.domain;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "acta")
@Getter
@Setter
@NoArgsConstructor
public class Acta {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @UuidGenerator(algorithm = UuidV7Generator.class)
  @Column(name = "public_id", unique = true, nullable = false, updatable = false)
  private UUID publicId;

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

  @Column(name = "storage_url", length = 512)
  private String storageUrl;

  @Column(name = "total_sufragantes", nullable = false)
  private Integer totalSufragantes;

  @Column(name = "votos_blancos", nullable = false)
  private Integer votosBlancos;

  @Column(name = "votos_nulos", nullable = false)
  private Integer votosNulos;

  @CreationTimestamp
  @Column(name = "fecha_registro", nullable = false, updatable = false)
  private Instant fechaRegistro;

  @Column(name = "motivo_anulacion", length = 500)
  private String motivoAnulacion;

  public static Acta nuevoBorrador() {
    return new Acta();
  }

  public void setTotalSufragantes(Integer totalSufragantes) {
    if (totalSufragantes != null && totalSufragantes < 0) {
      throw new ActaInvalidaException("El total de sufragantes no puede ser negativo");
    }
    this.totalSufragantes = totalSufragantes;
  }

  public void setVotosBlancos(Integer votosBlancos) {
    if (votosBlancos != null && votosBlancos < 0) {
      throw new ActaInvalidaException("Los votos blancos no pueden ser negativos");
    }
    this.votosBlancos = votosBlancos;
  }

  public void setVotosNulos(Integer votosNulos) {
    if (votosNulos != null && votosNulos < 0) {
      throw new ActaInvalidaException("Los votos nulos no pueden ser negativos");
    }
    this.votosNulos = votosNulos;
  }

  public void setMotivoAnulacion(String motivoAnulacion) {
    if (motivoAnulacion != null && motivoAnulacion.trim().isEmpty()) {
      throw new ActaInvalidaException("El motivo de anulación no puede estar vacío");
    }
    this.motivoAnulacion = motivoAnulacion;
  }

  public void validarDatosObligatorios() {
    if (this.jrv == null) {
      throw new ActaInvalidaException("JRV es obligatoria para el acta");
    }
    if (this.cargo == null) {
      throw new ActaInvalidaException("Cargo es obligatorio para el acta");
    }
    if (this.registradaPor == null) {
      throw new ActaInvalidaException("El usuario que registra es obligatorio");
    }
    if (this.totalSufragantes == null) {
      throw new ActaInvalidaException("El total de sufragantes es obligatorio");
    }
    if (this.votosBlancos == null) {
      throw new ActaInvalidaException("Los votos blancos son obligatorios");
    }
    if (this.votosNulos == null) {
      throw new ActaInvalidaException("Los votos nulos son obligatorios");
    }
  }
}
