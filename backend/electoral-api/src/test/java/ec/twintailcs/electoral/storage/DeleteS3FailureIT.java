package ec.twintailcs.electoral.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ec.twintailcs.electoral.evidencia.application.EvidenciaService;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Gate 1 — A.3: Reproduce el defecto del borrado que miente (RF-STO-04).
 *
 * <p>Hipótesis: {@code EliminarEvidenciaUseCase.execute()} captura la excepción de S3, la registra
 * con {@code log.error} y devuelve éxito ({@link ec.twintailcs.electoral.evidencia.application.usecase.EliminarEvidenciaUseCase}:50-58). El
 * frontend recibe "borrado" mientras la foto sigue accesible por presigned URL.
 *
 * <p>Diseño del test:
 * <ol>
 *   <li>Subir una evidencia (RustFS real, OK).
 *   <li>Detener el contenedor RustFS para forzar que {@code s3.delete()} falle con error de red.
 *   <li>Llamar {@code evidenciaService.delete(publicId, userId)} — debería propagar el error.
 *   <li>Verificar que NO propaga (catch se traga) y que el controller devolvería 204.
 * </ol>
 *
 * <p>Trampa explícita: S3StorageService NO se mockea. El fallo se induce apagando el contenedor
 * real (única manera de reproducir un error de red sin tocar código de producción).
 */
@SpringBootTest
@Testcontainers
class DeleteS3FailureIT {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("electoral_test");

  @Container
  static GenericContainer<?> s3container =
      new GenericContainer<>("rustfs/rustfs:latest")
          .withExposedPorts(9000)
          .withEnv("MINIO_ROOT_USER", "minioadmin")
          .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
          .waitingFor(
              new HttpWaitStrategy()
                  .forPath("/minio/health/live")
                  .forPort(9000)
                  .withStartupTimeout(Duration.ofSeconds(90)));

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add(
        "aws.s3.endpoint",
        () -> "http://" + s3container.getHost() + ":" + s3container.getMappedPort(9000));
    registry.add("aws.s3.region", () -> "us-east-1");
    registry.add("aws.s3.bucket", () -> "electoral-evidences");
    registry.add("aws.s3.path-style-access-enabled", () -> "true");
    System.setProperty("aws.access.key.id", "minioadmin");
    System.setProperty("aws.secret.access.key", "minioadmin");
  }

  @Autowired private EvidenciaService evidenciaService;
  @Autowired private S3StorageService s3;
  @Autowired private JdbcTemplate jdbc;

  private static S3Client adminClient;
  private Long observerId;
  private Long actaId;
  private Long jrvId;
  private Long cargoId;
  private UUID observerPublicId;
  private UUID evidenciaPublicId;

  @BeforeAll
  static void createBucket() {
    adminClient =
        S3Client.builder()
            .endpointOverride(
                URI.create("http://" + s3container.getHost() + ":" + s3container.getMappedPort(9000)))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("minioadmin", "minioadmin")))
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    try {
      adminClient.createBucket(b -> b.bucket("electoral-evidences"));
    } catch (Exception ignore) {
      // ya existe
    }
  }

  @BeforeEach
  void setupFixtures() {
    Long provinciaId =
        jdbc.queryForObject(
            "INSERT INTO provincia (nombre, codigo) VALUES (?,?) RETURNING id",
            Long.class,
            "Santa Elena",
            "24-" + UUID.randomUUID().toString().substring(0, 4));
    Long cantonId =
        jdbc.queryForObject(
            "INSERT INTO canton (nombre, codigo, provincia_id) VALUES (?,?,?) RETURNING id",
            Long.class,
            "Santa Elena",
            "2401-" + UUID.randomUUID().toString().substring(0, 4),
            provinciaId);
    Long parroquiaId =
        jdbc.queryForObject(
            "INSERT INTO parroquia (nombre, es_urbana, canton_id) VALUES (?,?,?) RETURNING id",
            Long.class,
            "Santa Elena",
            true,
            cantonId);

    jrvId =
        jdbc.queryForObject(
            "INSERT INTO jrv (numero, recinto, total_electores, parroquia_id) "
                + "VALUES (?,?,?,?) RETURNING id",
            Long.class,
            "JRV-" + UUID.randomUUID().toString().substring(0, 6),
            "Recinto Test",
            200,
            parroquiaId);
    cargoId =
        jdbc.queryForObject(
            "INSERT INTO cargo (nombre, nivel, puestos) VALUES (?,?,?) RETURNING id",
            Long.class,
            "Prefecto",
            "PROVINCIA",
            1);

    observerPublicId = UUID.randomUUID();
    Long rolObsId = jdbc.queryForObject("SELECT id FROM rol WHERE nombre='OBSERVER'", Long.class);
    observerId =
        jdbc.queryForObject(
            "INSERT INTO usuario (email, password_hash, rol_id, habilitado, nombre, apellido, "
                + "public_id, creado_en) VALUES (?,?,?,?,?,?,?,NOW()) RETURNING id",
            Long.class,
            "obs-" + UUID.randomUUID() + "@test.local",
            "$2a$12$" + "x".repeat(53),
            rolObsId,
            true,
            "Obs",
            "Test",
            observerPublicId);

    actaId =
        jdbc.queryForObject(
            "INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado, total_sufragantes, "
                + "votos_blancos, votos_nulos, public_id, version) "
                + "VALUES (?,?,?,?,?,?,?,?,0) RETURNING id",
            Long.class,
            jrvId,
            cargoId,
            observerId,
            "ENVIADA",
            100,
            5,
            3,
            UUID.randomUUID());

    // Subir una evidencia (RustFS arriba, upload funciona).
    MockMultipartFile file =
        new MockMultipartFile("file", "test.webp", "image/webp", "contenido-a3".getBytes());
    var evidencia =
        evidenciaService.upload(file, actaId, observerPublicId);
    evidenciaPublicId = evidencia.getPublicId();
  }

  /**
   * A.3 / B.4 — El delete PROPAGA el error cuando S3 falla.
   *
   * <p>Pre-condición: evidencia subida OK. Acción: detener RustFS (S3.delete falla por red).
   * Resultado esperado (post-fix B.4): el catch fue quitado → la excepción S3Exception
   * se propaga al controller → el FE recibe 5xx, no 204. **Eso es el comportamiento correcto**:
   * el usuario sabe que el borrado falló.
   *
   * <p>RED-GREEN: pre-fix el test pasaba (no excepción). Post-fix debe tirar.
   * La mutación C.2 reintroduce el catch → el test pasa de nuevo (regression caught).
   */
  @Test
  void deletePropagatesS3Failure() {
    // 1) Apagar RustFS para forzar error de red en S3.delete.
    s3container.stop();

    try {
      // 2) Post-fix B.4: el delete debe PROPAGAR la excepción. Verificamos que tira algo.
      assertThatCode(() -> evidenciaService.delete(evidenciaPublicId, observerPublicId))
          .as(
              "Post-fix B.4: delete debe lanzar excepción cuando S3 falla. "
                  + "Si NO lanza, el catch sigue tragándose el error (bug A.3).")
          .isNotNull(); // sanity: expresión ejecutada
      // La aserción real: capturar y verificar tipo
      Throwable thrown = null;
      try {
        evidenciaService.delete(evidenciaPublicId, observerPublicId);
      } catch (Throwable t) {
        thrown = t;
      }
      assertThat(thrown)
          .as("delete debe propagar S3Exception (o RuntimeException envolvente) cuando S3 falla")
          .isNotNull();
    } finally {
      // 3) Restaurar RustFS para otros tests.
      s3container.start();
    }
  }
}
