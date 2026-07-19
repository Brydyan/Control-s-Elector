package ec.twintailcs.electoral.evidencia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;

/**
 * Evidencia fotografica subida por un OBSERVER al digitalizar un acta.
 *
 * El binario vive en S3 (ver {@code s3_key}). En la DB guardamos
 * solo metadata: clave S3, tamano, hash, estado de revision.
 *
 * Decisiones de diseno:
 * - {@code @Version}: previene actualizaciones concurrentes (alguien aprueba
 *   mientras se sube). Hibernate lanza OptimisticLockException.
 * - indices en (jrv_id, cargo_id) y (usuario_id) por patron de consulta.
 * - {@code sha256} sin constraint unique ahora; cuando se integre con la mesa
 *   de actas se podra convertir en unique para deduplicacion.
 *
 * Las FK a JRV/Cargo/Usuario se modelaran como columnas long simples;
 * los modelos reales se completan cuando existan las otras tablas.
 */
@Entity
@Table(name = "evidencia", indexes = {
        @Index(name = "ix_evidencia_jrv_cargo", columnList = "jrv_id, cargo_id"),
        @Index(name = "ix_evidencia_usuario", columnList = "usuario_id"),
        @Index(name = "ix_evidencia_estado", columnList = "estado")
})
public class Evidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @UuidGenerator(algorithm = UuidV7Generator.class)
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "jrv_id", nullable = false)
    private Long jrvId;

    @Column(name = "cargo_id", nullable = false)
    private Long cargoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Path completo dentro del bucket: actas/{jrvId}/{cargoId}/{uuid}.webp */
    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    /** SHA-256 hex (64 chars). Nullable para no romper migracion; se llena en upload nuevo. */
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

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public void setPublicId(UUID publicId) { this.publicId = publicId; }
    public Long getJrvId() { return jrvId; }
    public void setJrvId(Long jrvId) { this.jrvId = jrvId; }
    public Long getCargoId() { return cargoId; }
    public void setCargoId(Long cargoId) { this.cargoId = cargoId; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public String getS3Key() { return s3Key; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public EstadoEvidencia getEstado() { return estado; }
    public void setEstado(EstadoEvidencia estado) { this.estado = estado; }
    public long getVersion() { return version; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
