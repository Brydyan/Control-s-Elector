package ec.twintailcs.electoral.storage.s3;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Define los beans singleton para acceder a S3.
 *
 * <p>- {@link S3Client}: cliente principal para todas las operaciones (PUT, GET, DELETE, LIST).
 * Thread-safe, mantiene HTTP connection pool automaticamente.
 *
 * <p>- {@link S3Presigner}: cliente separado SOLO para generar URLs prefirmadas (download temporal
 * seguro para el FE). AWS SDK lo provee como clase aparte porque presigner usa logica distinta.
 *
 * <p>Singleton via Spring: se construyen UNA vez al arrancar el contexto y se reutilizan en todos
 * los requests. Equivalente al patron singleton pero con lifecycle gestionado por Spring (cierre
 * limpio al shutdown).
 *
 * <p>Credenciales: - aws.access-key-id (default: rustfsadmin) - aws.secret-access-key (default:
 * rustfsadmin123) En prod (Railway/Supabase) se sobreescriben con secrets reales.
 */
@Configuration
public class S3Config {

  /**
   * Lee credenciales de env vars AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY con fallback a las
   * defaults del .properties. Spring Boot NO mapea automatic AWS_* env vars a aws.* properties, asi
   * que las leemos explicitamente.
   */
  private String accessKey() {
    String env = System.getenv("AWS_ACCESS_KEY_ID");
    if (env != null && !env.isBlank()) return env;
    String prop = System.getProperty("aws.access.key.id");
    if (prop != null && !prop.isBlank()) return prop;
    return "rustfsadmin";
  }

  private String secretKey() {
    String env = System.getenv("AWS_SECRET_ACCESS_KEY");
    if (env != null && !env.isBlank()) return env;
    String prop = System.getProperty("aws.secret.access.key");
    if (prop != null && !prop.isBlank()) return prop;
    return "rustfsadmin123";
  }

  @Bean
  public S3Client s3Client(S3Properties props) {
    var builder =
        S3Client.builder()
            .region(Region.of(props.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey(), secretKey())))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(props.isPathStyleAccessEnabled())
                    .build());

    if (props.getEndpoint() != null && !props.getEndpoint().isBlank()) {
      builder.endpointOverride(URI.create(props.getEndpoint()));
    }

    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner(S3Properties props) {
    var builder =
        S3Presigner.builder()
            .region(Region.of(props.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey(), secretKey())))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(props.isPathStyleAccessEnabled())
                    .build());

    if (props.getEndpoint() != null && !props.getEndpoint().isBlank()) {
      builder.endpointOverride(URI.create(props.getEndpoint()));
    }

    return builder.build();
  }
}
