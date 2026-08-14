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
class PartidoControllerTest {

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
    }

    private Rol obtenerRol(String nombre) {
        return rolRepo.findByNombre(nombre)
                .orElseThrow(() -> new RuntimeException(
                        "Rol " + nombre + " no encontrado. Liquibase debe haberlo sembrado."));
    }

    @Test
    void listar_returnsPartidos() throws Exception {
        Partido partido = new Partido("Partido Social Cristiano", "PSC", 6);
        partidoRepo.saveAndFlush(partido);

        mockMvc.perform(get("/api/v1/configuracion/partidos")
                        .header("Authorization", "Bearer " + observerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].siglas").value("PSC"));
    }

    @Test
    void listar_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/configuracion/partidos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crear_asMaster_withValidPayload_returns201() throws Exception {
        String body = "{\"nombre\":\"Alianza PAIS\",\"siglas\":\"AP\",\"numeroLista\":35}";

        mockMvc.perform(post("/api/v1/configuracion/partidos")
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.siglas").value("AP"));

        Partido saved = partidoRepo.findBySiglas("AP").orElseThrow();
        assertThat(saved.getNumeroLista()).isEqualTo(35);
        assertThat(auditLogRepo.findByAccion("CREAR_PARTIDO")).isNotEmpty();
    }

    @Test
    void crear_asObserver_returns403() throws Exception {
        String body = "{\"nombre\":\"Alianza PAIS\",\"siglas\":\"AP\",\"numeroLista\":35}";

        mockMvc.perform(post("/api/v1/configuracion/partidos")
                        .header("Authorization", "Bearer " + observerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        assertThat(partidoRepo.findBySiglas("AP")).isEmpty();
    }

    @Test
    void crear_withMissingSiglas_returns400() throws Exception {
        String body = "{\"nombre\":\"Alianza PAIS\",\"numeroLista\":35}";

        mockMvc.perform(post("/api/v1/configuracion/partidos")
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizar_asMaster_withValidPayload_returns200() throws Exception {
        Partido partido = new Partido("PSC", "PSC", 6);
        partido = partidoRepo.saveAndFlush(partido);

        String body = "{\"nombre\":\"Partido Social Cristiano\",\"siglas\":\"PSC\",\"numeroLista\":6}";

        mockMvc.perform(put("/api/v1/configuracion/partidos/{id}", partido.getId())
                        .header("Authorization", "Bearer " + masterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Partido Social Cristiano"));

        Partido actualizado = partidoRepo.findById(partido.getId()).orElseThrow();
        assertThat(actualizado.getNombre()).isEqualTo("Partido Social Cristiano");
    }

    @Test
    void actualizar_asObserver_returns403() throws Exception {
        Partido partido = new Partido("PSC", "PSC", 6);
        partido = partidoRepo.saveAndFlush(partido);

        String body = "{\"nombre\":\"Partido Social Cristiano\",\"siglas\":\"PSC\",\"numeroLista\":6}";

        mockMvc.perform(put("/api/v1/configuracion/partidos/{id}", partido.getId())
                        .header("Authorization", "Bearer " + observerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminar_asMaster_returns204() throws Exception {
        Partido partido = new Partido("Otro Partido", "OP", 40);
        partido = partidoRepo.saveAndFlush(partido);

        mockMvc.perform(delete("/api/v1/configuracion/partidos/{id}", partido.getId())
                        .header("Authorization", "Bearer " + masterToken))
                .andExpect(status().isNoContent());

        assertThat(partidoRepo.findById(partido.getId())).isEmpty();
    }

    @Test
    void eliminar_asObserver_returns403() throws Exception {
        Partido partido = new Partido("Otro Partido", "OP", 40);
        partido = partidoRepo.saveAndFlush(partido);

        mockMvc.perform(delete("/api/v1/configuracion/partidos/{id}", partido.getId())
                        .header("Authorization", "Bearer " + observerToken))
                .andExpect(status().isForbidden());

        assertThat(partidoRepo.findById(partido.getId())).isPresent();
    }
}
