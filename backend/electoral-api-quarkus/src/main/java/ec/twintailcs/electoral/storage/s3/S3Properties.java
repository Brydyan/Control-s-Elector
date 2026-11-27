package ec.twintailcs.electoral.storage.s3;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configuracion del bucket S3 (RustFS local / Supabase Storage en prod).
 *
 * <p>Port exacto de las {@code S3Properties} del backend Spring: mismas claves de configuracion
 * ({@code aws.s3.*}) y mismos nombres de variable de entorno ({@code AWS_S3_BUCKET}, {@code
 * AWS_S3_REGION}, {@code AWS_S3_ENDPOINT}, {@code AWS_S3_PATH_STYLE}, {@code
 * AWS_S3_PRESIGN_EXPIRY}).
 *
 * <p>Los defaults son los mismos que trae el {@code application.properties} del backend Spring,
 * para que las dos implementaciones apunten al mismo bucket con la misma configuracion aunque no se
 * pase ninguna variable de entorno.
 *
 * <p><b>Por que {@code AWS_S3_PATH_STYLE} se lee a mano.</b> El binding de MicroProfile Config
 * traduce el nombre de la propiedad a variable de entorno reemplazando puntos y guiones por guiones
 * bajos ({@code aws.s3.path-style-access-enabled} -> {@code AWS_S3_PATH_STYLE_ACCESS_ENABLED}), asi
 * que la variable corta que usa el backend Spring ({@code AWS_S3_PATH_STYLE}) no llega por el
 * binding. Se lee del entorno explicitamente, igual que Spring la resuelve por placeholder en su
 * {@code application.properties}.
 */
@ApplicationScoped
public class S3Properties {

  /** ID del bucket (ej: electoral-evidences). */
  private final String bucket;

  /** Region (us-east-1 por default). */
  private final String region;

  /** Endpoint custom (vacio = AWS real). Para RustFS va http://localhost:9000. */
  private final String endpoint;

  /**
   * Endpoint con el que se firman las URLs prefirmadas que abre el navegador. El host forma parte
   * de la firma, asi que no se puede reescribir despues: si el backend llega a S3 por un nombre
   * interno (en Docker, {@code http://rustfs:9000}), el navegador no lo resuelve. Vacio = se usa
   * {@link #endpoint}, que es lo correcto cuando el endpoint ya es publico (R2 en produccion).
   */
  private final String publicEndpoint;

  /** Path style para S3-compatible (RustFS, MinIO). True por default. */
  private final boolean pathStyleAccessEnabled;

  /**
   * Duracion de las presigned URLs (RF-STO-07). Por defecto 15 minutos. En prod se puede bajar a 5
   * minutos via configuracion sin tocar codigo.
   */
  private final Duration presignExpiry;

  public S3Properties(
      @ConfigProperty(name = "aws.s3.bucket", defaultValue = "electoral-evidences") String bucket,
      @ConfigProperty(name = "aws.s3.region", defaultValue = "us-east-1") String region,
      @ConfigProperty(name = "aws.s3.endpoint", defaultValue = "http://localhost:9000")
          String endpoint,
      @ConfigProperty(name = "aws.s3.public-endpoint") Optional<String> publicEndpoint,
      @ConfigProperty(name = "aws.s3.path-style-access-enabled", defaultValue = "true")
          boolean pathStyleAccessEnabled,
      @ConfigProperty(name = "aws.s3.presign-expiry", defaultValue = "PT15M")
          Duration presignExpiry) {
    this.bucket = bucket;
    this.region = region;
    this.endpoint = endpoint;
    this.publicEndpoint =
        publicEndpoint.map(String::trim).filter(valor -> !valor.isEmpty()).orElse(endpoint);
    this.pathStyleAccessEnabled = booleanFromEnv("AWS_S3_PATH_STYLE", pathStyleAccessEnabled);
    this.presignExpiry = presignExpiry;
  }

  /**
   * Lee un booleano del entorno. Si la variable no existe o esta vacia, gana el valor del binding
   * de configuracion.
   */
  private static boolean booleanFromEnv(String nombre, boolean porDefecto) {
    String valor = System.getenv(nombre);
    if (valor == null || valor.isBlank()) {
      return porDefecto;
    }
    return Boolean.parseBoolean(valor.trim());
  }

  public String getBucket() {
    return bucket;
  }

  public String getRegion() {
    return region;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getPublicEndpoint() {
    return publicEndpoint;
  }

  public boolean isPathStyleAccessEnabled() {
    return pathStyleAccessEnabled;
  }

  public Duration getPresignExpiry() {
    return presignExpiry;
  }
}
