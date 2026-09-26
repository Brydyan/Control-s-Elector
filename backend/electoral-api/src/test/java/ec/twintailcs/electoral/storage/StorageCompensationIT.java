package ec.twintailcs.electoral.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.evidencia.application.usecase.SubirEvidenciaUseCase;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Gate 1 — A.2: Reproduce el defecto de compensación S3 (RF-STO-03).
 *
 * <p>Hipótesis (de design.md D2): el catch en {@code SubirEvidenciaUseCase.execute()} rodea {@code
 * repository.save(...)}. Con JPA, save() solo ENCOLA el INSERT; el flush/commit ocurre cuando el
 * método retorna y Spring cierra la transacción. Una violación de constraint explota FUERA de ese
 * catch → el objeto queda huérfano en S3.
 *
 * <p>Diseño del test: contenedor RustFS real (Testcontainers, NO mock). Setup agrega un UNIQUE
 * INDEX sobre {@code evidencia.sha256} (la columna existe pero sin unique en el schema actual).
 * Pre-insertamos una fila con un {@code sha256} conocido. Ejecutamos el use case con un archivo
 * cuyo contenido produce exactamente ese mismo SHA-256 → el INSERT del nuevo row viola la UNIQUE
 * constraint en COMMIT (no en save()) → la excepción se lanza DESPUÉS de que execute() retornó → el
 * catch no la ve → S3 no se limpia → objeto huérfano.
 *
 * <p>Trampa explícita (per design.md D3): NO se mockea {@code S3StorageService}. La verificación va
 * contra RustFS real con un cliente S3 aparte.
 */
@SpringBootTest
@Testcontainers
class StorageCompensationIT {

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
    // S3Config lee aws.access.key.id / aws.secret.access.key via System.getProperty.
    System.setProperty("aws.access.key.id", "minioadmin");
    System.setProperty("aws.secret.access.key", "minioadmin");
  }

  @Autowired private SubirEvidenciaUseCase useCase;
  @Autowired private JdbcTemplate jdbc;

  private static S3Client adminClient;
  private Long observerId;
  private Long actaId;
  private Long jrvId;
  private Long cargoId;
  private UUID observerPublicId;

  @BeforeAll
  static void createBucket() {
    adminClient =
        S3Client.builder()
            .endpointOverride(
                URI.create(
                    "http://" + s3container.getHost() + ":" + s3container.getMappedPort(9000)))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("minioadmin", "minioadmin")))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    try {
      adminClient.createBucket(b -> b.bucket("electoral-evidences"));
    } catch (Exception ignore) {
      // ya existe
    }
  }

  @BeforeEach
  void setupFixtures() {
    // Schema ampliada para el test: UNIQUE constraint DEFERRABLE INITIALLY DEFERRED sobre
    // sha256. Esto es CLAVE: la constraint NO se chequea al INSERT (save() retorna OK), sino
    // al COMMIT. Eso reproduce exactamente el escenario del design.md D2: la excepción sale
    // DEL TransactionInterceptor, no del repository.save() — y por lo tanto NO entra al catch
    // alrededor de save() en SubirEvidenciaUseCase.execute().
    jdbc.execute("ALTER TABLE evidencia DROP CONSTRAINT IF EXISTS uq_test_evidencia_sha256");
    jdbc.execute(
        "ALTER TABLE evidencia ADD CONSTRAINT uq_test_evidencia_sha256 "
            + "UNIQUE (sha256) DEFERRABLE INITIALLY DEFERRED");

    // Provincia + Canton + Parroquia
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

    // JRV + Cargo
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

    // OBSERVER usuario
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

    // Acta
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
  }

  /**
   * A.2 / B.3 — Sin huérfano S3 cuando el INSERT falla en commit.
   *
   * <p>Pre-condición: fila en {@code evidencia} con sha256 = X + UNIQUE constraint DEFERRABLE
   * INITIALLY DEFERRED sobre sha256. Acción: ejecutar use case con archivo cuyo contenido hashea a
   * X. Resultado esperado (post-fix B.3): INSERT viola UNIQUE en COMMIT → rollback →
   * TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK) limpia S3 → SIN huérfano.
   *
   * <p>Este test es RED-GREEN: pre-fix fallaba (orphan existía); post-fix pasa (orphan limpiado).
   * La mutación C.1 revierte el fix y debe hacer caer el test.
   */
  @Test
  void dbCommitFailureLeavesNoOrphanInS3() throws Exception {
    long objectsBefore = countObjectsInBucket();

    // 1) Contenido cuyo SHA-256 vamos a pre-insertar.
    byte[] content = "contenido-que-produce-sha256-reproducible".getBytes();
    String expectedSha = sha256Hex(content);

    // 2) Pre-insertar fila con ese SHA-256.
    jdbc.update(
        """
        INSERT INTO evidencia
          (public_id, acta_id, jrv_id, cargo_id, usuario_id,
           s3_key, size_bytes, content_type, sha256, estado,
           version, creado_en, actualizado_en)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW(), NOW())
        """,
        UUID.randomUUID(),
        actaId,
        jrvId,
        cargoId,
        observerId,
        "actas/" + actaId + "/pre-existing.webp",
        (long) content.length,
        "image/webp",
        expectedSha,
        "EN_REVISION");

    // 3) Ejecutar use case — la constraint UNIQUE deferida falla en COMMIT.
    MockMultipartFile file = new MockMultipartFile("file", "test.webp", "image/webp", content);

    assertThatThrownBy(() -> useCase.execute(file, actaId, observerPublicId))
        .isInstanceOfAny(DataIntegrityViolationException.class, RuntimeException.class);

    // 4) Post-fix B.3: TransactionSynchronization limpió el objeto en S3. NO debe haber huérfano.
    long objectsAfter = countObjectsInBucket();
    assertThat(objectsAfter)
        .as(
            "Post-fix B.3: NO debe haber huérfano en RustFS tras rollback. "
                + "antes=%d después=%d. Si después > antes, el cleanup on rollback falló.",
            objectsBefore, objectsAfter)
        .isEqualTo(objectsBefore);
  }

  private static String sha256Hex(byte[] content) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(md.digest(content));
  }

  private long countObjectsInBucket() {
    List<S3Object> objs =
        adminClient
            .listObjectsV2(ListObjectsV2Request.builder().bucket("electoral-evidences").build())
            .contents();
    return objs.size();
  }

  /**
   * C.5 — Persistencia de la referencia (RF-STO-01).
   *
   * <p>Tras subir un acta, debe existir una fila en {@code evidencia} con el {@code s3_key} del
   * objeto recién subido. La fila en {@code evidencia} ES la referencia a la foto del acta (después
   * de B.2: el acta ya no tiene storage_key; la fuente de verdad es la tabla evidencia vinculada
   * por {@code acta_id}).
   *
   * <p>Pre-fix: el test pasa (evidencia persistida). Post-fix: igual pasa. La mutación C.5 (remover
   * repository.save(evidencia)) rompe la persistencia → conteo queda en 0 → test cae.
   */
  @Test
  void uploadedFileHasReferencePersisted() {
    MockMultipartFile file =
        new MockMultipartFile("file", "ref-test.webp", "image/webp", "contenido-c5".getBytes());

    long evidenciaCountBefore = countEvidenciaRowsForActa();
    var evidencia = useCase.execute(file, actaId, observerPublicId);
    long evidenciaCountAfter = countEvidenciaRowsForActa();

    assertThat(evidenciaCountAfter - evidenciaCountBefore)
        .as(
            "Post-fix B.3/B.5: subir un acta debe persistir la fila en evidencia con el s3_key. "
                + "Si después == antes, la persistencia de la referencia fue removida.")
        .isEqualTo(1L);

    // Verificamos que el s3_key de la fila coincide con el patrón actas/{actaId}/uuid.webp.
    String persistedKey =
        jdbc.queryForObject(
            "SELECT s3_key FROM evidencia WHERE id = ?", String.class, evidencia.getId());
    assertThat(persistedKey)
        .as("s3_key persistido debe seguir el patrón actas/{actaId}/{uuid}.webp")
        .startsWith("actas/" + actaId + "/")
        .endsWith(".webp");
  }

  private long countEvidenciaRowsForActa() {
    Long count =
        jdbc.queryForObject("SELECT COUNT(*) FROM evidencia WHERE acta_id = ?", Long.class, actaId);
    return count == null ? 0L : count;
  }
}
