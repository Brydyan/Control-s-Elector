package ec.twintailcs.electoral.evidencia.domain;

import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;
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
 * Evidencia fotografica subida por un OBSERVER al digitalizar un acta.
 *
 * <p>El binario vive en S3 ({@code s3_key}); en la base guardamos solo metadata.
 *
 * <p>Port exacto de la entidad del backend Spring. El {@code acta} es una FK ({@code acta_id}) y
 * no se mapea como entidad: el modulo de evidencias no recorre hacia el acta (solo necesita su
 * identificador y su geometria, que copia en {@code jrv_id} y {@code cargo_id}), asi que se mapea
 * como columna simple, igual que hace el modulo de geografia con {@code provincia_id}. Por el
 * mismo motivo las observaciones ({@code evidencia_observacion.evidencia_id}) se consultan por id
 * y no como coleccion lazy: asi el DTO siempre ve lo que esta en la base, sin depender de una
 * coleccion inicializada en otra transaccion.
 *
 * <p>Los nombres de columna van EXPLICITOS: Quarkus no aplica la conversion CamelCaseToUnderscores
 * que aplica Spring, asi que declararlos es lo que hace que las dos implementaciones mapeen el
 * mismo schema.
 */
@Entity
@Table(name = "evidencia")
public class Evidencia extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @UuidGenerator(algorithm = UuidV7Generator.class)
  @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "UUID")
  UUID publicId;

  /** Acta a la que se asocia la evidencia. */
  @Column(name = "acta_id", nullable = false)
  Long actaId;

  @Column(name = "jrv_id", nullable = false)
  Long jrvId;

  @Column(name = "cargo_id", nullable = false)
  Long cargoId;

  @Column(name = "usuario_id", nullable = false)
  Long usuarioId;

  /** Path completo dentro del bucket: {@code actas/{actaId}/{uuid}.webp} */
  @Column(name = "s3_key", nullable = false, length = 512)
  String s3Key;

  @Column(name = "size_bytes", nullable = false)
  long sizeBytes;

  @Column(name = "content_type", nullable = false, length = 64)
  String contentType;

  @Column(name = "sha256", length = 64)
  String sha256;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado", nullable = false, length = 16)
  EstadoEvidencia estado = EstadoEvidencia.EN_REVISION;

  @Version
  @Column(name = "version", nullable = false)
  long version;

  @Column(name = "creado_en", nullable = false, updatable = false)
  Instant creadoEn = Instant.now();

  @Column(name = "actualizado_en", nullable = false)
  Instant actualizadoEn = Instant.now();

  public Long getId() {
    return id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public Long getActaId() {
    return actaId;
  }

  public void setActaId(Long actaId) {
    this.actaId = actaId;
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

  public Long getUsuarioId() {
    return usuarioId;
  }

  public void setUsuarioId(Long usuarioId) {
    this.usuarioId = usuarioId;
  }

  public String getS3Key() {
    return s3Key;
  }

  public void setS3Key(String s3Key) {
    if (s3Key == null || s3Key.trim().isEmpty()) {
      throw new EvidenciaInvalidaException("La clave S3 no puede estar vacía");
    }
    this.s3Key = s3Key;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(long sizeBytes) {
    if (sizeBytes <= 0) {
      throw new EvidenciaInvalidaException("El tamaño del archivo debe ser mayor a 0 bytes");
    }
    this.sizeBytes = sizeBytes;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    if (contentType == null || contentType.trim().isEmpty()) {
      throw new EvidenciaInvalidaException("El tipo de contenido no puede estar vacío");
    }
    this.contentType = contentType;
  }

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String sha256) {
    this.sha256 = sha256;
  }

  public EstadoEvidencia getEstado() {
    return estado;
  }

  public void setEstado(EstadoEvidencia estado) {
    this.estado = estado;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public void setCreadoEn(Instant creadoEn) {
    this.creadoEn = creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  public void setActualizadoEn(Instant actualizadoEn) {
    this.actualizadoEn = actualizadoEn;
  }

  /** Invariantes obligatorias de la evidencia. Mismas comprobaciones que el backend Spring. */
  public void validarDatosObligatorios() {
    if (this.actaId == null) {
      throw new EvidenciaInvalidaException("El acta es obligatoria");
    }
    if (this.jrvId == null) {
      throw new EvidenciaInvalidaException("El ID de JRV es obligatorio");
    }
    if (this.cargoId == null) {
      throw new EvidenciaInvalidaException("El ID de cargo es obligatorio");
    }
    if (this.usuarioId == null) {
      throw new EvidenciaInvalidaException("El ID de usuario es obligatorio");
    }
    if (this.s3Key == null || this.s3Key.trim().isEmpty()) {
      throw new EvidenciaInvalidaException("La clave S3 es obligatoria");
    }
    if (this.contentType == null || this.contentType.trim().isEmpty()) {
      throw new EvidenciaInvalidaException("El tipo de contenido es obligatorio");
    }
    if (this.sizeBytes <= 0) {
      throw new EvidenciaInvalidaException("El tamaño del archivo debe ser mayor a 0 bytes");
    }
  }
}
