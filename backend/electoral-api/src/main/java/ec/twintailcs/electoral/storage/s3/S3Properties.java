package ec.twintailcs.electoral.storage.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracion del bucket S3 (RustFS local / Supabase Storage en prod).
 *
 * <p>Las propiedades se leen de application*.properties bajo el prefijo `aws.s3.*`. Ver {@link
 * application.properties} para los defaults.
 *
 * <p>Sensible a cambios en runtime: cuando se modifica una propiedad, Spring reinicia el contexto
 * si se usa @RefreshScope o se implementa el patron de recarga correspondiente.
 */
@Component
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

  /** ID del bucket (ej: electoral-evidences). */
  private String bucket;

  /** Region (us-east-1 por default). */
  private String region = "us-east-1";

  /** Endpoint custom (null = AWS real). Para RustFS va http://localhost:9000. */
  private String endpoint;

  /** Path style para S3-compatible (RustFS, MinIO). True por default. */
  private boolean pathStyleAccessEnabled = true;

  /**
   * Duración de las presigned URLs (RF-STO-07). Por defecto 15 minutos. En prod Railway se
   * puede bajar a 5 minutos vía configuración sin tocar código.
   */
  private java.time.Duration presignExpiry = java.time.Duration.ofMinutes(15);

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public boolean isPathStyleAccessEnabled() {
    return pathStyleAccessEnabled;
  }

  public void setPathStyleAccessEnabled(boolean pathStyleAccessEnabled) {
    this.pathStyleAccessEnabled = pathStyleAccessEnabled;
  }

  public java.time.Duration getPresignExpiry() {
    return presignExpiry;
  }

  public void setPresignExpiry(java.time.Duration presignExpiry) {
    this.presignExpiry = presignExpiry;
  }
}
