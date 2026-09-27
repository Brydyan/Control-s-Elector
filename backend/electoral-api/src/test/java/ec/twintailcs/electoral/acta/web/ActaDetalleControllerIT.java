package ec.twintailcs.electoral.acta.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.twintailcs.electoral.auth.config.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T-BE-21: tests de integración del endpoint de detalle completo del acta.
 *
 * <ul>
 *   <li>C.1 — COORDINATOR puede ver el detalle con detalles + urlEvidencia.
 *   <li>C.2 — OBSERVER dueño ve 200, OBSERVER ajeno ve 403.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ActaDetalleControllerIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("electoral_test_detalle");

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.liquibase.enabled", () -> "true");
    r.add("aws.s3.endpoint", () -> "http://127.0.0.1:1");
    r.add("aws.s3.region", () -> "us-east-1");
    r.add("aws.s3.bucket", () -> "test-bucket");
    r.add("aws.s3.path-style-access-enabled", () -> "true");
    System.setProperty("aws.access.key.id", "minioadmin");
    System.setProperty("aws.secret.access.key", "minioadmin");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  private Long actaId;
  private UUID observerAPublicId;
  private UUID observerBPublicId;
  private UUID coordinatorPublicId;

  @BeforeEach
  @Transactional
  void setup() {
    jdbc.execute(
        "TRUNCATE evidencia_observacion, evidencia, acta, acta_detalle, "
            + "audit_log, notificacion, observer_jrv_asignacion, candidato, "
            + "jrv_cargo, cargo, partido, jrv, parroquia, canton, provincia, "
            + "usuario RESTART IDENTITY CASCADE");

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
    Long jrvId =
        jdbc.queryForObject(
            "INSERT INTO jrv (numero, recinto, total_electores, parroquia_id) "
                + "VALUES (?,?,?,?) RETURNING id",
            Long.class,
            "JRV-DET-1",
            "Recinto Test",
            200,
            parroquiaId);
    Long cargoId =
        jdbc.queryForObject(
            "INSERT INTO cargo (nombre, nivel, puestos) VALUES (?,?,?) RETURNING id",
            Long.class,
            "Prefecto",
            "PROVINCIA",
            1);
    Long partidoId =
        jdbc.queryForObject(
            "INSERT INTO partido (nombre, siglas, numero_lista) VALUES (?,?,?) RETURNING id",
            Long.class,
            "PARTIDO A",
            "PA",
            1);
    Long candidatoId =
        jdbc.queryForObject(
            "INSERT INTO candidato (nombre, orden, cargo_id, partido_id) "
                + "VALUES (?,?,?,?) RETURNING id",
            Long.class,
            "C1",
            1,
            cargoId,
            partidoId);
    jdbc.update(
        "INSERT INTO jrv_cargo (jrv_id, cargo_id, activo) VALUES (?,?,true)", jrvId, cargoId);

    observerAPublicId = crearUsuario("OBSERVER", "obs-a");
    observerBPublicId = crearUsuario("OBSERVER", "obs-b");
    coordinatorPublicId = crearUsuario("COORDINATOR", "coord");

    Long obsAId =
        jdbc.queryForObject(
            "SELECT id FROM usuario WHERE public_id = ?", Long.class, observerAPublicId);
    jdbc.update(
        "INSERT INTO observer_jrv_asignacion (usuario_id, jrv_id) VALUES (?,?)", obsAId, jrvId);

    actaId =
        jdbc.queryForObject(
            "INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado, total_sufragantes, "
                + "votos_blancos, votos_nulos, public_id, version) "
                + "VALUES (?,?,?,?,?,?,?,?,0) RETURNING id",
            Long.class,
            jrvId,
            cargoId,
            obsAId,
            "EN_DISPUTA",
            100,
            5,
            3,
            UUID.randomUUID());
    jdbc.update(
        "INSERT INTO acta_detalle (acta_id, candidato_id, votos, version) VALUES (?,?,?,0)",
        actaId,
        candidatoId,
        90);

    // Audit con motivo de disputa
    jdbc.update(
        "INSERT INTO audit_log (entidad, entidad_id, accion, detalle, registrado_por, timestamp) "
            + "VALUES (?,?,?,CAST(? AS jsonb),?,NOW())",
        "acta",
        actaId,
        "ACTA_EN_DISPUTA",
        "{\"motivo\":\"duda en conteo\"}",
        obsAId);
  }

  private UUID crearUsuario(String rolNombre, String emailPrefix) {
    Long rolId = jdbc.queryForObject("SELECT id FROM rol WHERE nombre=?", Long.class, rolNombre);
    UUID publicId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario (email, password_hash, rol_id, habilitado, nombre, apellido, "
            + "public_id, creado_en) VALUES (?,?,?,?,?,?,?,NOW())",
        emailPrefix + "-" + UUID.randomUUID() + "@test.local",
        "$2a$12$" + "x".repeat(53),
        rolId,
        true,
        "Test",
        rolNombre,
        publicId);
    return publicId;
  }

  private Authentication authAs(UUID publicId, String rol, String... perms) {
    UserPrincipal principal = new UserPrincipal(publicId, "test@test", rol, List.of(perms));
    var auths = new java.util.ArrayList<SimpleGrantedAuthority>();
    auths.add(new SimpleGrantedAuthority("ROLE_" + rol));
    for (String p : perms) auths.add(new SimpleGrantedAuthority(p));
    return new UsernamePasswordAuthenticationToken(principal, "n/a", auths);
  }

  // -------------------------------------------------------------------------
  // C.1 — COORDINATOR ve el detalle
  // -------------------------------------------------------------------------

  @Test
  void coordinador_veDetalleConDetallesYUrlEvidencia() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/coordinator/actas/{actaId}", actaId)
                .with(authentication(authAs(coordinatorPublicId, "COORDINATOR", "acta:read"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(actaId))
        .andExpect(jsonPath("$.estado").value("EN_DISPUTA"))
        .andExpect(jsonPath("$.detalles").isArray())
        .andExpect(jsonPath("$.detalles[0].votos").value(90))
        .andExpect(jsonPath("$.detalles[0].candidatoId").exists())
        .andExpect(jsonPath("$.motivoDisputa").exists())
        // motivoDisputa contiene el JSON serializado (toString del Map)
        .andExpect(jsonPath("$.motivoDisputa").value(org.hamcrest.Matchers.containsString("duda")));
  }

  // -------------------------------------------------------------------------
  // C.2 — OBSERVER dueño ve 200, OBSERVER ajeno ve 403
  // -------------------------------------------------------------------------

  @Test
  void observerDuenio_veDetalle() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/observer/actas/{actaId}", actaId)
                .with(authentication(authAs(observerAPublicId, "OBSERVER", "acta:upload"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(actaId));
  }

  @Test
  void observerAjeno_noVeDetalle_devuelve403() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/observer/actas/{actaId}", actaId)
                .with(authentication(authAs(observerBPublicId, "OBSERVER", "acta:upload"))))
        .andExpect(status().isForbidden());
  }
}
