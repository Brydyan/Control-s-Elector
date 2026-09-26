package ec.twintailcs.electoral.incidencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.twintailcs.electoral.auth.config.UserPrincipal;
import ec.twintailcs.electoral.incidencia.domain.TipoIncidenciaJrv;
import ec.twintailcs.electoral.incidencia.web.dto.IncidenciaRequestDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
 * T-BE-19: tests de integración del módulo de incidencias operativas de JRV.
 *
 * <p>Cubre:
 *
 * <ul>
 *   <li>S1 — Reporte de incidencia por veedor (POST /api/v1/observer/incidencias) → 201.
 *   <li>S2 — Consulta por coordinador (GET /api/v1/coordinator/incidencias) → 200 con datos.
 *   <li>S3 — Rechazo de JRV inexistente → 404.
 *   <li>Bloqueo a usuarios anónimos / sin rol.
 * </ul>
 *
 * <p>Mutaciones:
 *
 * <ul>
 *   <li>V.1 — omitir el seteo de {@code reportadaPor} → violación NOT NULL en BD.
 *   <li>V.2 — quitar {@code @PreAuthorize} del listado COORDINATOR → 200 sin auth.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IncidenciaJrvIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("electoral_test_inc");

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.liquibase.enabled", () -> "true");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private Long jrvId;
  private UUID observerPublicId;
  private UUID coordinatorPublicId;

  @BeforeEach
  @Transactional
  void setup() {
    jdbc.execute(
        "TRUNCATE audit_log, notificacion, incidencia_jrv, observer_jrv_asignacion, "
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
    jrvId =
        jdbc.queryForObject(
            "INSERT INTO jrv (numero, recinto, total_electores, parroquia_id) "
                + "VALUES (?,?,?,?) RETURNING id",
            Long.class,
            "JRV-" + UUID.randomUUID().toString().substring(0, 6),
            "Recinto Test",
            200,
            parroquiaId);

    observerPublicId = crearUsuario("OBSERVER", "obs");
    coordinatorPublicId = crearUsuario("COORDINATOR", "coord");

    Long obsId =
        jdbc.queryForObject(
            "SELECT id FROM usuario WHERE public_id = ?", Long.class, observerPublicId);
    jdbc.update(
        "INSERT INTO observer_jrv_asignacion (usuario_id, jrv_id) VALUES (?,?)", obsId, jrvId);
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
  // S1 — Reporte de incidencia por veedor
  // -------------------------------------------------------------------------

  @Test
  void observador_registra_incidencia_devuelve201() throws Exception {
    IncidenciaRequestDto dto =
        new IncidenciaRequestDto(
            jrvId, TipoIncidenciaJrv.IMPEDIMENTO_VEEDOR, "Policía impidió paso al aula", null);

    mockMvc
        .perform(
            post("/api/v1/observer/incidencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(authentication(authAs(observerPublicId, "OBSERVER", "acta:upload"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tipo").value("IMPEDIMENTO_VEEDOR"))
        .andExpect(jsonPath("$.descripcion").value("Policía impidió paso al aula"));

    Long count = jdbc.queryForObject("SELECT COUNT(*) FROM incidencia_jrv", Long.class);
    assertThat(count).isEqualTo(1L);

    Long auditCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE accion = 'INCIDENCIA_JRV_REGISTRADA'",
            Long.class);
    assertThat(auditCount)
        .as("debe haber 1 fila de audit con accion INCIDENCIA_JRV_REGISTRADA")
        .isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // S3 — Rechazo de JRV inexistente
  // -------------------------------------------------------------------------

  @Test
  void jrvInexistente_devuelve404() throws Exception {
    IncidenciaRequestDto dto =
        new IncidenciaRequestDto(99999L, TipoIncidenciaJrv.OTRO, "test", null);

    mockMvc
        .perform(
            post("/api/v1/observer/incidencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(authentication(authAs(observerPublicId, "OBSERVER", "acta:upload"))))
        .andExpect(status().isNotFound());
  }

  // -------------------------------------------------------------------------
  // S2 — Listado por COORDINATOR
  // -------------------------------------------------------------------------

  @Test
  void coordinador_lista_incidencias() throws Exception {
    // Sembrar 2 incidencias
    IncidenciaRequestDto d1 =
        new IncidenciaRequestDto(jrvId, TipoIncidenciaJrv.URNA_VIOLENTADA, "a", null);
    IncidenciaRequestDto d2 =
        new IncidenciaRequestDto(jrvId, TipoIncidenciaJrv.CORTE_ENERGIA, "b", null);
    mockMvc
        .perform(
            post("/api/v1/observer/incidencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(d1))
                .with(authentication(authAs(observerPublicId, "OBSERVER", "acta:upload"))))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/observer/incidencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(d2))
                .with(authentication(authAs(observerPublicId, "OBSERVER", "acta:upload"))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/v1/coordinator/incidencias")
                .with(authentication(authAs(coordinatorPublicId, "COORDINATOR", "acta:read"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
  }

  // -------------------------------------------------------------------------
  // Bloqueo de seguridad
  // -------------------------------------------------------------------------

  @Test
  void observador_listaComo_coordinador_devuelve403() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/coordinator/incidencias")
                .with(authentication(authAs(observerPublicId, "OBSERVER", "acta:upload"))))
        .andExpect(status().isForbidden());
  }
}
