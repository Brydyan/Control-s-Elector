package ec.twintailcs.electoral.evidencia.domain;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Evidencia fotográfica subida por un OBSERVER al digitalizar un acta.
 *
 * <p>El binario vive en S3 ({@code s3_key}); en DB guardamos solo metadata.
 */
@Entity
@Table(
    name = "evidencia",
    indexes = {
      @Index(name = "ix_evidencia_acta", columnList = "acta_id"),
      @Index(name = "ix_evidencia_jrv_cargo", columnList = "jrv_id, cargo_id"),
      @Index(name = "ix_evidencia_usuario", columnList = "usuario_id"),
      @Index(name = "ix_evidencia_estado", columnList = "estado")
    })
@Getter
@Setter
@NoArgsConstructor
public class Evidencia {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @UuidGenerator(algorithm = UuidV7Generator.class)
  @Column(name = "public_id", nullable = false, unique = true, updatable = false)
  private UUID publicId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "acta_id", nullable = false)
  private Acta acta;

  @Column(name = "jrv_id", nullable = false)
  private Long jrvId;

  @Column(name = "cargo_id", nullable = false)
  private Long cargoId;

  @Column(name = "usuario_id", nullable = false)
  private Long usuarioId;

  /** Path completo dentro del bucket: actas/{actaId}/{uuid}.webp */
  @Column(name = "s3_key", nullable = false, length = 512)
  private String s3Key;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "content_type", nullable = false, length = 64)
  private String contentType;

  @Column(name = "sha256", length = 64)
  private String sha256;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado", nullable = false, length = 16)
  private EstadoEvidencia estado = EstadoEvidencia.EN_REVISION;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn = Instant.now();

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn = Instant.now();

  public void setSizeBytes(long sizeBytes) {
    if (sizeBytes <= 0) {
      throw new EvidenciaInvalidaException("El tamaño del archivo debe ser mayor a 0 bytes");
    }
    this.sizeBytes = sizeBytes;
  }

  public void setS3Key(String s3Key) {
    if (s3Key == null || s3Key.trim().isEmpty()) {
      throw new EvidenciaInvalidaException("La clave S3 no puede estar vacía");
    }
    this.s3Key = s3Key;
  }

  public void setContentType(String contentType) {
    if (contentType == null || contentType.trim().isEmpty()) {
      throw new EvidenciaInvalidaException("El tipo de contenido no puede estar vacío");
    }
    this.contentType = contentType;
  }

  public void validarDatosObligatorios() {
    if (this.acta == null) {
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
