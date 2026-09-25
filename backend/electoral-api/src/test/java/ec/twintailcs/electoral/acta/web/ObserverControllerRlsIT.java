package ec.twintailcs.electoral.acta.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.auth.config.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * RF-TEST-04 escenario 1 vía HTTP (cierra WARNING 1 del verifier de T-BE-15).
 *
 * <p>El test ejercita el camino completo: MockMvc → Spring Security filter → JWT-less auth con
 * UserPrincipal inyectado en SecurityContext → ObserverController.misActas → ActaRepository.
 *
 * <p>El WARN de {@code authOrNull()} en {@code CurrentUser} (rama donde principal no es
 * UserPrincipal) se evita inyectando {@link UserPrincipal} como principal directamente.
 *
 * <p>Mutación C.1: quitar el filtro de {@code registradaPorId} en {@code ActaRepository.
 * findWithFilters}. El test debe caer (devuelve 2 actas, no 1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ObserverControllerRlsIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("electoral_test_obs");

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.liquibase.enabled", () -> "true");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ActaService actaService;
  @Autowired private ActaRepository actaRepository;
  @Autowired private JdbcTemplate jdbc;

  private Long jrv1Id;
  private Long jrv2Id;
  private UUID observerAPublicId;
  private UUID observerBPublicId;
  private Long cargoId;
  private Long candidatoId;
  private Acta actaA;
  private Acta actaB;

  @BeforeEach
  @Transactional
  void setup() {
    // Limpiar geografía, actas, usuarios de test. NO truncar seeds (rol, permiso, menu, etc.).
    jdbc.execute(
        "TRUNCATE evidencia_observacion, evidencia, acta, acta_detalle, "
            + "audit_log, notificacion, observer_jrv_asignacion, candidato, "
            + "jrv_cargo, cargo, partido, jrv, parroquia, canton, provincia, "
            + "usuario RESTART IDENTITY CASCADE");

    // Geografía mínima
    Long provinciaId =
        jdbc.queryForObject(
            "INSERT INTO provincia (nombre, codigo) VALUES (?,?) RETURNING id",
            Long.class, "Santa Elena", "24-" + UUID.randomUUID().toString().substring(0, 4));
    Long cantonId =
        jdbc.queryForObject(
            "INSERT INTO canton (nombre, codigo, provincia_id) VALUES (?,?,?) RETURNING id",
            Long.class, "La Libertad",
            "2402-" + UUID.randomUUID().toString().substring(0, 4), provinciaId);
    Long parroquiaId =
        jdbc.queryForObject(
            "INSERT INTO parroquia (nombre, es_urbana, canton_id) VALUES (?,?,?) RETURNING id",
            Long.class, "Central", true, cantonId);

    jrv1Id =
        jdbc.queryForObject(
            "INSERT INTO jrv (numero, recinto, total_electores, parroquia_id) "
                + "VALUES (?,?,?,?) RETURNING id",
            Long.class, "JRV-100", "Recinto 1", 200, parroquiaId);
    jrv2Id =
        jdbc.queryForObject(
            "INSERT INTO jrv (numero, recinto, total_electores, parroquia_id) "
                + "VALUES (?,?,?,?) RETURNING id",
            Long.class, "JRV-101", "Recinto 2", 200, parroquiaId);

    cargoId =
        jdbc.queryForObject(
            "INSERT INTO cargo (nombre, nivel, puestos) VALUES (?,?,?) RETURNING id",
            Long.class, "Prefecto", "PROVINCIA", 1);

    // Partido + candidato (necesarios para acta_detalle)
    Long partidoId =
        jdbc.queryForObject(
            "INSERT INTO partido (nombre, siglas, numero_lista) VALUES (?,?,?) RETURNING id",
            Long.class, "PARTIDO", "PAR", 1);
    candidatoId =
        jdbc.queryForObject(
            "INSERT INTO candidato (nombre, orden, cargo_id, partido_id) "
                + "VALUES (?,?,?,?) RETURNING id",
            Long.class, "C1", 1, cargoId, partidoId);

    jdbc.update(
        "INSERT INTO jrv_cargo (jrv_id, cargo_id, activo) VALUES (?,?,true)", jrv1Id, cargoId);
    jdbc.update(
        "INSERT INTO jrv_cargo (jrv_id, cargo_id, activo) VALUES (?,?,true)", jrv2Id, cargoId);

    // 2 OBSERVERs
    observerAPublicId = crearObserver("observer-a");
    observerBPublicId = crearObserver("observer-b");

    // Asignaciones: A→JRV-1, B→JRV-2
    Long observerAId =
        jdbc.queryForObject(
            "SELECT id FROM usuario WHERE public_id = ?", Long.class, observerAPublicId);
    Long observerBId =
        jdbc.queryForObject(
            "SELECT id FROM usuario WHERE public_id = ?", Long.class, observerBPublicId);
    jdbc.update(
        "INSERT INTO observer_jrv_asignacion (usuario_id, jrv_id) VALUES (?,?)",
        observerAId, jrv1Id);
    jdbc.update(
        "INSERT INTO observer_jrv_asignacion (usuario_id, jrv_id) VALUES (?,?)",
        observerBId, jrv2Id);

    // 2 actas: A en JRV-1, B en JRV-2
    Long actaAId =
        jdbc.queryForObject(
            "INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado, total_sufragantes, "
                + "votos_blancos, votos_nulos, public_id, version) "
                + "VALUES (?,?,?,?,?,?,?,?,0) RETURNING id",
            Long.class, jrv1Id, cargoId, observerAId, "ENVIADA", 100, 5, 3, UUID.randomUUID());
    jdbc.update(
        "INSERT INTO acta_detalle (acta_id, candidato_id, votos, version) VALUES (?,?,?,0)",
        actaAId, candidatoId, 90);

    Long actaBId =
        jdbc.queryForObject(
            "INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado, total_sufragantes, "
                + "votos_blancos, votos_nulos, public_id, version) "
                + "VALUES (?,?,?,?,?,?,?,?,0) RETURNING id",
            Long.class, jrv2Id, cargoId, observerBId, "ENVIADA", 100, 5, 3, UUID.randomUUID());
    jdbc.update(
        "INSERT INTO acta_detalle (acta_id, candidato_id, votos, version) VALUES (?,?,?,0)",
        actaBId, candidatoId, 90);

    actaA = actaRepository.findById(actaAId).orElseThrow();
    actaB = actaRepository.findById(actaBId).orElseThrow();
  }

  private UUID crearObserver(String emailPrefix) {
    Long rolId = jdbc.queryForObject("SELECT id FROM rol WHERE nombre='OBSERVER'", Long.class);
    UUID publicId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario (email, password_hash, rol_id, habilitado, nombre, apellido, "
            + "public_id, creado_en) VALUES (?,?,?,?,?,?,?,NOW())",
        emailPrefix + "-" + UUID.randomUUID() + "@test.local",
        "$2a$12$" + "x".repeat(53),
        rolId,
        true,
        "Test",
        "OBSERVER",
        publicId);
    return publicId;
  }

  /**
   * Construye un Authentication con UserPrincipal en el SecurityContext, para que
   * CurrentUser.id() devuelva el publicId del OBSERVER B.
   */
  private Authentication authAs(UUID userPublicId) {
    UserPrincipal principal =
        new UserPrincipal(userPublicId, "test@test", "OBSERVER", List.of("evidencia:read", "acta:upload"));
    return new UsernamePasswordAuthenticationToken(
        principal, "n/a", List.of(
            new SimpleGrantedAuthority("ROLE_OBSERVER"),
            new SimpleGrantedAuthority("evidencia:read"),
            new SimpleGrantedAuthority("acta:upload")));
  }

  // -------------------------------------------------------------------------
  // B.1 — Test del escenario 1 de RF-TEST-04 (listado)
  // -------------------------------------------------------------------------

  @Test
  void observerBNoVeActasDelObserverA() throws Exception {
    final Long esperadoJrvB = jrv2Id;
    mockMvc
        .perform(get("/api/v1/observer/mis-actas").with(authentication(authAs(observerBPublicId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].jrvId", is(esperadoJrvB.intValue())));
  }
}
