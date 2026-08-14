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
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.NivelCargo;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
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
 * Migrado de @WebMvcTest a @SpringBootTest para que JdbcTemplate/DataSource
 * esten disponibles (RlsInterceptor los requiere) y para probar la
 * autorizacion real via JWT + @PreAuthorize (no mocks de CurrentUser).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class CandidatoControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandidatoRepository candidatoRepo;

    @Autowired
    private CargoRepository cargoRepo;

    @Autowired
    private PartidoRepository partidoRepo;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private RolRepository rolRepo;

    @Autowired
    private AuditLogRepository auditLogRepo;

    @Autowired
    private JwtService jwtService;

    private String masterToken;
    private String observerToken;
    private Cargo cargo;
    private Partido partido;

    @BeforeEach
    void setUp() {
        Rol rolMaster = obtenerRol("MASTER");
        Rol rolObserver = obtenerRol("OBSERVER");

        Usuario master = new Usuario();
        master.setEmail("master-" + UUID.randomUUID() + "@test.local");
        master.setPasswordHash("hash");
        master.setRol(rolMaster);
        master.setHabilitado(true);
        master = usuarioRepo.saveAndFlush(master);

        Usuario observer = new Usuario();
        observer.setEmail("observer-" + UUID.randomUUID() + "@test.local");
        observer.setPasswordHash("hash");
        observer.setRol(rolObserver);
        observer.setHabilitado(true);
        observer = usuarioRepo.saveAndFlush(observer);

        masterToken = jwtService.issue(master.getPublicId(), master.getEmail(), "MASTER",
                List.of("configuracion:write"), "session-master").access();
        observerToken = jwtService.issue(observer.getPublicId(), observer.getEmail(), "OBSERVER",
                List.of(), "session-observer").access();

        cargo = new Cargo("Alcalde/sa", NivelCargo.CANTON, 1);
        cargo = cargoRepo.saveAndFlush(cargo);
        partido = new Partido("PSC", "PSC", 6);
        partido = partidoRepo.saveAndFlush(partido);
    }

    private Rol obtenerRol(String nombre) {
        return rolRepo.findByNombre(nombre)
                .orElseThrow(() -> new RuntimeException(
                        "Rol " + nombre + " no encontrado. Liquibase debe haberlo sembrado."));
    }

    @Test
    void listar_returnsCandidatos() throws Exception {
        Candidato candidato = new Candidato("Juan Perez", 1, cargo, partido);
        candidatoRepo.saveAndFlush(candidato);

        mockMvc.perform(get("/api/v1/configuracion/candidatos")
                        .header("Authorization", "Bearer " + observerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Juan Perez"))
                .andExpect(jsonPath("$[0].cargoId").value(cargo.getId()))
                .andExpect(jsonPath("$[0].partidoId").value(partido.getId()));
    }

    @Test
    void listar_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/configuracion/candidatos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_conFiltros_pasaParametrosAlServicio() throws Exception {
        Candidato candidato = new Candidato("Juan Perez", 1, cargo, partido);
        candidatoRepo.saveAndFlush(candidato);

        mockMvc.perform(get("/api/v1/configuracion/candidatos")
                        .header("Authorization", "Bearer " + observerToken)
                        .param("cargoId", String.valueOf(cargo.getId()))
                        .param("partidoId", String.valueOf(partido.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Juan Perez"));
    }

    @Test
    void crear_asMaster_withValidPayload_returns201() throws Exception {
        String body = String.format(
                "{\"nombre\":\"Maria Lopez\",\"orden\":2,\"cargoId\":%d,\"partidoId\":%d}",
                cargo.getId(), partido.getId());

        mockMvc.perform(post("/api/v1/configuracion/candidatos")
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Maria Lopez"));

        assertThat(candidatoRepo.findByCargoId(cargo.getId()))
                .anyMatch(c -> c.getNombre().equals("Maria Lopez"));
        assertThat(auditLogRepo.findByAccion("CREAR_CANDIDATO")).isNotEmpty();
    }

    @Test
    void crear_asObserver_returns403() throws Exception {
        String body = String.format(
                "{\"nombre\":\"Maria Lopez\",\"orden\":2,\"cargoId\":%d,\"partidoId\":%d}",
                cargo.getId(), partido.getId());

        mockMvc.perform(post("/api/v1/configuracion/candidatos")
                        .header("Authorization", "Bearer " + observerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        assertThat(candidatoRepo.findByCargoId(cargo.getId())).isEmpty();
    }

    @Test
    void crear_withMissingCargoId_returns400() throws Exception {
        String body = String.format(
                "{\"nombre\":\"Maria Lopez\",\"orden\":2,\"partidoId\":%d}",
                partido.getId());

        mockMvc.perform(post("/api/v1/configuracion/candidatos")
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizar_asMaster_withValidPayload_returns200() throws Exception {
        Candidato candidato = new Candidato("Juan Perez", 1, cargo, partido);
        candidato = candidatoRepo.saveAndFlush(candidato);

        String body = String.format(
                "{\"nombre\":\"Juan Perez Actualizado\",\"orden\":1,\"cargoId\":%d,\"partidoId\":%d}",
                cargo.getId(), partido.getId());

        mockMvc.perform(put("/api/v1/configuracion/candidatos/{id}", candidato.getId())
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juan Perez Actualizado"));

        Candidato actualizado = candidatoRepo.findById(candidato.getId()).orElseThrow();
        assertThat(actualizado.getNombre()).isEqualTo("Juan Perez Actualizado");
    }

    @Test
    void actualizar_asObserver_returns403() throws Exception {
        Candidato candidato = new Candidato("Juan Perez", 1, cargo, partido);
        candidato = candidatoRepo.saveAndFlush(candidato);

        String body = String.format(
                "{\"nombre\":\"Juan Perez Actualizado\",\"orden\":1,\"cargoId\":%d,\"partidoId\":%d}",
                cargo.getId(), partido.getId());

        mockMvc.perform(put("/api/v1/configuracion/candidatos/{id}", candidato.getId())
                        .header("Authorization", "Bearer " + observerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminar_asMaster_returns204() throws Exception {
        Candidato candidato = new Candidato("Pedro Ruiz", 3, cargo, partido);
        candidato = candidatoRepo.saveAndFlush(candidato);

        mockMvc.perform(delete("/api/v1/configuracion/candidatos/{id}", candidato.getId())
                        .header("Authorization", "Bearer " + masterToken))
                .andExpect(status().isNoContent());

        assertThat(candidatoRepo.findById(candidato.getId())).isEmpty();
    }

    @Test
    void eliminar_asObserver_returns403() throws Exception {
        Candidato candidato = new Candidato("Pedro Ruiz", 3, cargo, partido);
        candidato = candidatoRepo.saveAndFlush(candidato);

        mockMvc.perform(delete("/api/v1/configuracion/candidatos/{id}", candidato.getId())
                        .header("Authorization", "Bearer " + observerToken))
                .andExpect(status().isForbidden());

        assertThat(candidatoRepo.findById(candidato.getId())).isPresent();
    }
}
