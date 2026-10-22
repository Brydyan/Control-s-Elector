package ec.twintailcs.electoral.storage.s3;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Define los clientes singleton para acceder a S3.
 *
 * <p>Port de la {@code S3Config} del backend Spring: el cliente se construye a mano (no con la
 * extension {@code quarkus-amazon-s3}) para que el modelo de configuracion sea el mismo y el
 * contrato de almacenamiento no cambie.
 *
 * <ul>
 *   <li>{@link S3Client}: cliente principal para todas las operaciones (PUT, GET, DELETE). Es
 *       thread-safe y mantiene el pool de conexiones por su cuenta.
 *   <li>{@link S3Presigner}: cliente separado SOLO para generar URLs prefirmadas. El SDK lo provee
 *       aparte porque el presigner usa logica distinta.
 * </ul>
 *
 * <p>Los dos se producen como singletons: se construyen UNA vez al arrancar y se reutilizan en
 * todos los requests. El cierre es limpio al apagar la aplicacion.
 *
 * <p>Credenciales: {@code AWS_ACCESS_KEY_ID} / {@code AWS_SECRET_ACCESS_KEY} (defaults {@code
 * rustfsadmin} / {@code rustfsadmin123}, los mismos que el backend Spring). En prod se
 * sobreescriben con los secrets reales.
 */
@ApplicationScoped
public class S3Config {

  /**
   * Cliente principal de S3.
   *
   * <p>Se construye a mano con el endpoint, la region, las credenciales y el modo path-style de
   * {@link S3Properties}: exactamente el mismo ensamblado que hace el backend Spring.
   */
  @Produces
  @Singleton
  public S3Client s3Client(S3Properties props) {
    var builder =
        S3Client.builder()
            .httpClient(ApacheHttpClient.builder().build())
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

  /** Cliente de prefirmado de URLs de descarga. */
  @Produces
  @Singleton
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

  /** Cierra el cliente de S3 al apagar la aplicacion. */
  void cerrarS3Client(@Disposes S3Client client) {
    client.close();
  }

  /** Cierra el presigner al apagar la aplicacion. */
  void cerrarS3Presigner(@Disposes S3Presigner presigner) {
    presigner.close();
  }

  /**
   * Credencial de acceso. Se lee del entorno porque el binding de configuracion de MicroProfile no
   * traduce {@code aws.access-key-id} al nombre corto que usa el backend Spring en su {@code
   * application.properties}; el fallback cubre los dos caminos.
   */
  private String accessKey() {
    String env = System.getenv("AWS_ACCESS_KEY_ID");
    if (env != null && !env.isBlank()) {
      return env;
    }
    return System.getProperty("aws.access.key.id", "rustfsadmin");
  }

  /** Credencial secreta. Misma logica que {@link #accessKey()}. */
  private String secretKey() {
    String env = System.getenv("AWS_SECRET_ACCESS_KEY");
    if (env != null && !env.isBlank()) {
      return env;
    }
    return System.getProperty("aws.secret.access.key", "rustfsadmin123");
  }
}
