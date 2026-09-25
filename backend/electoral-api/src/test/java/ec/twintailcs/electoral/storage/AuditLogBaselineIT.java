package ec.twintailcs.electoral.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.evidencia.application.EvidenciaService;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
 * Gate 1 — A.4: Baseline de auditoría RF-STO-05.
 *
 * <p>Mide cuántas filas en {@code audit_log} se generan tras un upload + delete de evidencia, ANTES
 * del fix B.5. La spec original (spec.md) asume 0 filas ("hoy ninguna se audita"). El código real
 * (a 2026-09-25) ya audita el delete en {@code EliminarEvidenciaUseCase:60-65} pero NO el upload.
 * Por lo tanto el baseline real es 1 fila (solo delete).
 *
 * <p>Este test documenta el baseline. Después del fix B.5 (auditar upload), el conteo debe subir a
 * 2. La mutación C.3 (quitar audit del delete) hace bajar el conteo a 1.
 */
@SpringBootTest
@Testcontainers
class AuditLogBaselineIT {

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
  }

  /**
   * A.4 / B.5 — Upload y delete quedan auditados.
   *
   * <p>Cuenta filas en {@code audit_log} con acciones de evidencia tras un upload + delete.
   * Post-fix B.5: ambos quedan auditados → 2 filas (EVIDENCIA_SUBIDA + EVIDENCIA_ELIMINADA).
   *
   * <p>RED-GREEN: pre-B.5 el test esperaba 1 (solo delete). Post-B.5 espera 2. Mutación C.3
   * (quitar audit del delete) lleva a 1; mutación C.5 (no persistir ref) lleva a 1 (solo
   * delete, porque el upload falla antes de commit).
   */
  @Test
  void uploadAndDeleteAreBothAudited() {
    long auditCountBefore = countAuditRowsForEvidencia();

    MockMultipartFile file =
        new MockMultipartFile("file", "test.webp", "image/webp", "contenido-a4".getBytes());
    var evidencia = evidenciaService.upload(file, actaId, observerPublicId);

    long auditCountAfterUpload = countAuditRowsForEvidencia();
    System.out.println(
        "[DIAG A.4] audit_log rows tras upload = " + (auditCountAfterUpload - auditCountBefore));

    evidenciaService.delete(evidencia.getPublicId(), observerPublicId);

    long auditCountAfterDelete = countAuditRowsForEvidencia();
    System.out.println(
        "[DIAG A.4] audit_log rows tras delete = " + (auditCountAfterDelete - auditCountBefore));

    // Detalle
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT accion, entidad FROM audit_log "
                + "WHERE accion LIKE 'EVIDENCIA_%' OR entidad = 'evidencia' "
                + "ORDER BY id");
    System.out.println("[DIAG A.4] audit rows = " + rows.size());
    for (Map<String, Object> r : rows) {
      System.out.println(
          "[DIAG A.4]   accion=" + r.get("accion") + " entidad=" + r.get("entidad"));
    }

    // Post-fix B.5: ambos auditados → 2 filas.
    long delta = auditCountAfterDelete - auditCountBefore;
    assertThat(delta)
        .as(
            "Post-fix B.5: upload y delete deben quedar auditados (2 filas). "
                + "Si es 1, falta una auditoría. Si es 0, falta ambas.")
        .isEqualTo(2L);
  }

  private long countAuditRowsForEvidencia() {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log "
                + "WHERE accion LIKE 'EVIDENCIA_%' OR entidad = 'evidencia'",
            Long.class);
    return count == null ? 0L : count;
  }
}
