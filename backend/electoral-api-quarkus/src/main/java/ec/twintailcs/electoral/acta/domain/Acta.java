package ec.twintailcs.electoral.acta.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Acta electoral: el registro de los votos de una JRV para un cargo.
 *
 * <p>Mismo schema que la entidad del backend Spring. Las FKs a {@code jrv}, {@code cargo} y {@code
 * usuario} se mapean como columnas simples y no como asociaciones: esas tablas son de los modulos
 * de geografia, electoral y auth, y el modulo de actas solo necesita sus identificadores (los
 * nombres y numeros se leen con consultas de proyeccion). Asi la entidad no tiene proxies perezosos
 * que disparar un {@code LazyInitializationException} al mapear el DTO fuera de la transaccion, que
 * es el problema que el backend Spring tuvo que resolver con {@code ActaAssociations}.
 *
 * <p>Los nombres de columna van EXPLICITOS: Quarkus no aplica la conversion CamelCaseToUnderscores
 * que aplica Spring, asi que declararlos es lo que hace que las dos implementaciones mapeen el
 * mismo schema.
 *
 * <p>La tabla tiene RLS activada y forzada: un OBSERVER solo ve y escribe las actas que registro.
 */
@Entity
@Table(name = "acta")
public class Acta extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  /** Version de bloqueo optimista: dos transiciones concurrentes sobre la misma acta chocan aca. */
  @Version
  @Column(name = "version", nullable = false)
  Long version;

  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @Column(
      name = "public_id",
      nullable = false,
      unique = true,
      updatable = false,
      columnDefinition = "UUID")
  UUID publicId;

  /** JRV donde se registro el acta. */
  @Column(name = "jrv_id", nullable = false)
  Long jrvId;

  /** Cargo electoral que compite en la JRV. */
  @Column(name = "cargo_id", nullable = false)
  Long cargoId;

  /** Usuario que registro el acta (el OBSERVER autor). */
  @Column(name = "registrada_por", nullable = false)
  Long registradaPorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado", nullable = false, length = 20)
  EstadoActa estado = EstadoActa.BORRADOR;

  @Column(name = "total_sufragantes", nullable = false)
  Integer totalSufragantes;

  @Column(name = "votos_blancos", nullable = false)
  Integer votosBlancos;

  @Column(name = "votos_nulos", nullable = false)
  Integer votosNulos;

  @Column(name = "fecha_registro", nullable = false, updatable = false)
  Instant fechaRegistro = Instant.now();

  @Column(name = "motivo_anulacion", length = 500)
  String motivoAnulacion;

  public Long getId() {
    return id;
  }

  public Long getJrvId() {
    return jrvId;
  }

  public void setJrvId(Long jrvId) {
    this.jrvId = jrvId;
  }

  public Long getCargoId() {
    return cargoId;
  }

  public void setCargoId(Long cargoId) {
    this.cargoId = cargoId;
  }

  public Long getRegistradaPorId() {
    return registradaPorId;
  }

  public void setRegistradaPorId(Long registradaPorId) {
    this.registradaPorId = registradaPorId;
  }

  public EstadoActa getEstado() {
    return estado;
  }

  public void setEstado(EstadoActa estado) {
    this.estado = estado;
  }

  public Integer getTotalSufragantes() {
    return totalSufragantes;
  }

  public void setTotalSufragantes(Integer totalSufragantes) {
    if (totalSufragantes != null && totalSufragantes < 0) {
      throw new ActaInvalidaException("El total de sufragantes no puede ser negativo");
    }
    this.totalSufragantes = totalSufragantes;
  }

  public Integer getVotosBlancos() {
    return votosBlancos;
  }

  public void setVotosBlancos(Integer votosBlancos) {
    if (votosBlancos != null && votosBlancos < 0) {
      throw new ActaInvalidaException("Los votos blancos no pueden ser negativos");
    }
    this.votosBlancos = votosBlancos;
  }

  public Integer getVotosNulos() {
    return votosNulos;
  }

  public void setVotosNulos(Integer votosNulos) {
    if (votosNulos != null && votosNulos < 0) {
      throw new ActaInvalidaException("Los votos nulos no pueden ser negativos");
    }
    this.votosNulos = votosNulos;
  }

  public Instant getFechaRegistro() {
    return fechaRegistro;
  }

  public String getMotivoAnulacion() {
    return motivoAnulacion;
  }

  public void setMotivoAnulacion(String motivoAnulacion) {
    if (motivoAnulacion != null && motivoAnulacion.trim().isEmpty()) {
      throw new ActaInvalidaException("El motivo de anulación no puede estar vacío");
    }
    this.motivoAnulacion = motivoAnulacion;
  }

  /** Invariantes obligatorias del acta. Mismas comprobaciones que el backend Spring. */
  public void validarDatosObligatorios() {
    if (this.jrvId == null) {
      throw new ActaInvalidaException("JRV es obligatoria para el acta");
    }
    if (this.cargoId == null) {
      throw new ActaInvalidaException("Cargo es obligatorio para el acta");
    }
    if (this.registradaPorId == null) {
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
