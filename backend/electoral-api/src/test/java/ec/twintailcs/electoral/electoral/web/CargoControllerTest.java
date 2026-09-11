package ec.twintailcs.electoral.electoral.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.twintailcs.electoral.auditlog.domain.AuditLogRepository;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test de integracion HTTP end-to-end: controller -> service -> RLS -> DB.
 *
 * <p>Migrado de @WebMvcTest a @SpringBootTest para que JdbcTemplate/DataSource esten disponibles
 * (RlsAuthenticationBridge + RlsTransactionExecutionListener los requieren para resolver userId via
 * JPA y bindear el contexto RLS en afterBegin() sobre la conexion transaccional) y para probar la
 * autorizacion real via JWT + @PreAuthorize (no mocks de CurrentUser).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class CargoControllerTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.liquibase.enabled", () -> "true");
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private CargoRepository cargoRepo;

  @Autowired private UsuarioRepository usuarioRepo;

  @Autowired private RolRepository rolRepo;

  @Autowired private AuditLogRepository auditLogRepo;

  @Autowired private JwtService jwtService;

  private String masterToken;
  private String observerToken;

  @BeforeEach
  void setUp() {
    Rol rolMaster = obtenerRol("MASTER");
    Rol rolObserver = obtenerRol("OBSERVER");

    Usuario master = new Usuario();
    master.setEmail("master-" + UUID.randomUUID() + "@test.local");
    master.setNombre("Master");
    master.setApellido("Test");
    master.setPasswordHash("hash");
    master.setRol(rolMaster);
    master.setHabilitado(true);
    master = usuarioRepo.saveAndFlush(master);

    Usuario observer = new Usuario();
    observer.setEmail("observer-" + UUID.randomUUID() + "@test.local");
    observer.setNombre("Observer");
    observer.setApellido("Test");
    observer.setPasswordHash("hash");
    observer.setRol(rolObserver);
    observer.setHabilitado(true);
    observer = usuarioRepo.saveAndFlush(observer);

    masterToken =
        jwtService
            .issue(
                master.getPublicId(),
                master.getEmail(),
                "MASTER",
                List.of("configuracion:write"),
                "session-master")
            .access();
    observerToken =
        jwtService
            .issue(
                observer.getPublicId(),
                observer.getEmail(),
                "OBSERVER",
                List.of(),
                "session-observer")
            .access();
  }

  private Rol obtenerRol(String nombre) {
    return rolRepo
        .findByNombre(nombre)
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Rol " + nombre + " no encontrado. Liquibase debe haberlo sembrado."));
  }

  @Test
  void listar_returnsCargos() throws Exception {
    Cargo cargo = new Cargo("Alcalde/sa", NivelCargo.CANTON, 1);
    cargoRepo.saveAndFlush(cargo);

    mockMvc
        .perform(
            get("/api/v1/configuracion/cargos").header("Authorization", "Bearer " + observerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].nombre").value("Alcalde/sa"))
        .andExpect(jsonPath("$[0].nivel").value("CANTON"));
  }

  @Test
  void listar_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/configuracion/cargos")).andExpect(status().isUnauthorized());
  }

  @Test
  void crear_asMaster_withValidPayload_returns201() throws Exception {
    String body = "{\"nombre\":\"Prefecto/a\",\"nivel\":\"PROVINCIA\",\"puestos\":1}";

    mockMvc
        .perform(
            post("/api/v1/configuracion/cargos")
                .header("Authorization", "Bearer " + masterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nombre").value("Prefecto/a"))
        .andExpect(jsonPath("$.nivel").value("PROVINCIA"));

    Cargo saved = cargoRepo.findByNombre("Prefecto/a").orElseThrow();
    assertThat(saved.getNivel()).isEqualTo(NivelCargo.PROVINCIA);
    assertThat(auditLogRepo.findByAccion("CREAR_CARGO")).isNotEmpty();
  }

  @Test
  void crear_asObserver_returns403() throws Exception {
    String body = "{\"nombre\":\"Prefecto/a\",\"nivel\":\"PROVINCIA\",\"puestos\":1}";

    mockMvc
        .perform(
            post("/api/v1/configuracion/cargos")
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    assertThat(cargoRepo.findByNombre("Prefecto/a")).isEmpty();
  }

  @Test
  void crear_withBlankNombre_returns400() throws Exception {
    String body = "{\"nombre\":\"\",\"nivel\":\"PROVINCIA\",\"puestos\":1}";

    mockMvc
        .perform(
            post("/api/v1/configuracion/cargos")
                .header("Authorization", "Bearer " + masterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void crear_withMissingNivel_returns400() throws Exception {
    String body = "{\"nombre\":\"Alcalde\",\"puestos\":1}";

    mockMvc
        .perform(
            post("/api/v1/configuracion/cargos")
                .header("Authorization", "Bearer " + masterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void actualizar_asMaster_withValidPayload_returns200() throws Exception {
    Cargo cargo = new Cargo("Alcalde/sa", NivelCargo.CANTON, 1);
    cargo = cargoRepo.saveAndFlush(cargo);

    String body = "{\"nombre\":\"Alcalde/sa\",\"nivel\":\"CANTON\",\"puestos\":2}";

    mockMvc
        .perform(
            put("/api/v1/configuracion/cargos/{id}", cargo.getId())
                .header("Authorization", "Bearer " + masterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.puestos").value(2));

    Cargo actualizado = cargoRepo.findById(cargo.getId()).orElseThrow();
    assertThat(actualizado.getPuestos()).isEqualTo(2);
  }

  @Test
  void actualizar_asObserver_returns403() throws Exception {
    Cargo cargo = new Cargo("Alcalde/sa", NivelCargo.CANTON, 1);
    cargo = cargoRepo.saveAndFlush(cargo);

    String body = "{\"nombre\":\"Alcalde/sa\",\"nivel\":\"CANTON\",\"puestos\":2}";

    mockMvc
        .perform(
            put("/api/v1/configuracion/cargos/{id}", cargo.getId())
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void eliminar_asMaster_returns204() throws Exception {
    Cargo cargo = new Cargo("Concejal Urbano", NivelCargo.CANTON, 5);
    cargo = cargoRepo.saveAndFlush(cargo);

    mockMvc
        .perform(
            delete("/api/v1/configuracion/cargos/{id}", cargo.getId())
                .header("Authorization", "Bearer " + masterToken))
        .andExpect(status().isNoContent());

    assertThat(cargoRepo.findById(cargo.getId())).isEmpty();
  }

  @Test
  void eliminar_asObserver_returns403() throws Exception {
    Cargo cargo = new Cargo("Concejal Urbano", NivelCargo.CANTON, 5);
    cargo = cargoRepo.saveAndFlush(cargo);

    mockMvc
        .perform(
            delete("/api/v1/configuracion/cargos/{id}", cargo.getId())
                .header("Authorization", "Bearer " + observerToken))
        .andExpect(status().isForbidden());

    assertThat(cargoRepo.findById(cargo.getId())).isPresent();
  }
}
