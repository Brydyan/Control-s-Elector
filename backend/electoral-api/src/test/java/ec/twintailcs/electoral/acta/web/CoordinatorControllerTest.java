package ec.twintailcs.electoral.acta.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
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
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.CantonRepository;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.JrvRepository;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.domain.ParroquiaRepository;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.domain.ProvinciaRepository;
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
 * Test de integracion HTTP end-to-end: CoordinatorController -> ActaService -> DB.
 *
 * <p>Patron: @SpringBootTest + Testcontainers + JWT real via JwtService.issue(). Prueba ciclo
 * completo de disputa/correccion/aprobacion/anulacion de actas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class CoordinatorControllerTest {

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

  @Autowired private ActaRepository actaRepo;

  @Autowired private UsuarioRepository usuarioRepo;

  @Autowired private RolRepository rolRepo;

  @Autowired private AuditLogRepository auditLogRepo;

  @Autowired private JwtService jwtService;

  @Autowired private ProvinciaRepository provinciaRepo;

  @Autowired private CantonRepository cantonRepo;

  @Autowired private ParroquiaRepository parroquiaRepo;

  @Autowired private JrvRepository jrvRepo;

  @Autowired private CargoRepository cargoRepo;

  @Autowired private PartidoRepository partidoRepo;

  @Autowired private CandidatoRepository candidatoRepo;

  private String coordinatorToken;
  private String masterToken;
  private String observerToken;
  private Usuario observer;
  private Usuario coordinator;
  private Usuario master;
  private Jrv jrv;
  private Cargo cargo;
  private Candidato candidatoA;
  private Candidato candidatoB;

  @BeforeEach
  void setUp() {
    Rol rolObserver =
        rolRepo
            .findByNombre("OBSERVER")
            .orElseThrow(() -> new RuntimeException("Rol OBSERVER no encontrado"));
    Rol rolCoordinator =
        rolRepo
            .findByNombre("COORDINATOR")
            .orElseThrow(() -> new RuntimeException("Rol COORDINATOR no encontrado"));
    Rol rolMaster =
        rolRepo
            .findByNombre("MASTER")
            .orElseThrow(() -> new RuntimeException("Rol MASTER no encontrado"));

    observer = new Usuario();
    observer.setEmail("observer-" + UUID.randomUUID() + "@test.local");
    observer.setNombre("Observer");
    observer.setApellido("Test");
    observer.setPasswordHash("hash");
    observer.setRol(rolObserver);
    observer.setHabilitado(true);
    observer = usuarioRepo.saveAndFlush(observer);

    coordinator = new Usuario();
    coordinator.setEmail("coordinator-" + UUID.randomUUID() + "@test.local");
    coordinator.setNombre("Coordinator");
    coordinator.setApellido("Test");
    coordinator.setPasswordHash("hash");
    coordinator.setRol(rolCoordinator);
    coordinator.setHabilitado(true);
    coordinator = usuarioRepo.saveAndFlush(coordinator);

    master = new Usuario();
    master.setEmail("master-" + UUID.randomUUID() + "@test.local");
    master.setNombre("Master");
    master.setApellido("Test");
    master.setPasswordHash("hash");
    master.setRol(rolMaster);
    master.setHabilitado(true);
    master = usuarioRepo.saveAndFlush(master);

    // Permisos espejados del seed liquibase (002-rbac-seed.sql +
    // 012-acta-anular-permiso.sql). El controller ahora autoriza por
    // granularidad (acta:aprobar/rechazar/disputar/read/anular), asi
    // que los JWTs de test deben llevar las mismas authorities que
    // un usuario real obtendria al loguearse. OBSERVER se mantiene
    // SIN ninguno de los granulares para preservar el 403 negativo.
    observerToken =
        jwtService
            .issue(
                observer.getPublicId(),
                observer.getEmail(),
                "OBSERVER",
                List.of("acta:upload"),
                "session-observer")
            .access();
    coordinatorToken =
        jwtService
            .issue(
                coordinator.getPublicId(),
                coordinator.getEmail(),
                "COORDINATOR",
                List.of("acta:read", "acta:aprobar", "acta:rechazar"),
                "session-coordinator")
            .access();
    masterToken =
        jwtService
            .issue(
                master.getPublicId(),
                master.getEmail(),
                "MASTER",
                List.of("acta:read", "acta:aprobar", "acta:rechazar", "acta:anular"),
                "session-master")
            .access();

    Provincia provincia = provinciaRepo.saveAndFlush(new Provincia("Santa Elena", "24"));
    Canton canton = new Canton();
    canton.setNombre("Santa Elena");
    canton.setCodigo("2401");
    canton.setProvincia(provincia);
    canton = cantonRepo.saveAndFlush(canton);

    Parroquia parroquia = new Parroquia();
    parroquia.setNombre("Santa Elena");
    parroquia.setEsUrbana(true);
    parroquia.setCanton(canton);
    parroquia = parroquiaRepo.saveAndFlush(parroquia);

    jrv = new Jrv();
    jrv.setNumero("JRV-" + UUID.randomUUID().toString().substring(0, 8));
    jrv.setRecinto("Escuela Test");
    jrv.setTotalElectores(300);
    jrv.setParroquia(parroquia);
    jrv = jrvRepo.saveAndFlush(jrv);

    cargo = cargoRepo.saveAndFlush(new Cargo("Alcalde/sa", NivelCargo.CANTON, 1));

    Partido partidoA = partidoRepo.saveAndFlush(new Partido("Partido A", "PA", 1));
    Partido partidoB = partidoRepo.saveAndFlush(new Partido("Partido B", "PB", 2));

    candidatoA = candidatoRepo.saveAndFlush(new Candidato("Candidato A", 1, cargo, partidoA));
    candidatoB = candidatoRepo.saveAndFlush(new Candidato("Candidato B", 2, cargo, partidoB));
  }

  private Acta crearActaEnviada() {
    return crearActaEnviada(cargo);
  }

  private Acta crearActaEnviada(Cargo cargoParaActa) {
    Acta acta = new Acta();
    acta.setJrv(jrv);
    acta.setCargo(cargoParaActa);
    acta.setRegistradaPor(observer);
    acta.setEstado(EstadoActa.ENVIADA);
    acta.setTotalSufragantes(50);
    acta.setVotosBlancos(2);
    acta.setVotosNulos(1);
    return actaRepo.saveAndFlush(acta);
  }

  private String actaCorreccionBody() {
    return """
            {
              "jrvId": %d, "cargoId": %d,
              "totalSufragantes": 55,
              "votosBlancos": 3, "votosNulos": 2,
              "detalles": [
                {"candidatoId": %d, "votos": 35},
                {"candidatoId": %d, "votos": 15}
              ]
            }
            """
        .formatted(jrv.getId(), cargo.getId(), candidatoA.getId(), candidatoB.getId());
  }

  @Test
  void listarActas_retornaActasEnviadas() throws Exception {
    crearActaEnviada();

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("estado", "ENVIADA")
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].jrvId").value(jrv.getId()))
        .andExpect(jsonPath("$.data[0].estado").value("ENVIADA"))
        .andExpect(jsonPath("$.meta.page").value(0))
        .andExpect(jsonPath("$.meta.size").value(20))
        .andExpect(jsonPath("$.meta.totalElements").value(1))
        .andExpect(jsonPath("$.meta.totalPages").value(1));
  }

  @Test
  void listarActas_conFiltroCargoId() throws Exception {
    crearActaEnviada();

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("cargoId", String.valueOf(cargo.getId()))
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].cargoId").value(cargo.getId()));
  }

  @Test
  void listarActas_conAliasDignidadId_filtraPorCargo() throws Exception {
    crearActaEnviada();

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("dignidadId", String.valueOf(cargo.getId()))
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].cargoId").value(cargo.getId()))
        .andExpect(jsonPath("$.meta.totalElements").value(1));
  }

  @Test
  void listarActas_paginacion_segundaPaginaVacia() throws Exception {
    crearActaEnviada();

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("page", "1")
                .param("size", "10")
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.meta.page").value(1))
        .andExpect(jsonPath("$.meta.size").value(10))
        .andExpect(jsonPath("$.meta.totalElements").value(1))
        .andExpect(jsonPath("$.meta.totalPages").value(1));
  }

  @Test
  void listarActas_conSearchPorNumeroJrv_encuentraActa() throws Exception {
    crearActaEnviada();

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("search", jrv.getNumero().substring(0, 6))
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].jrvId").value(jrv.getId()))
        .andExpect(jsonPath("$.meta.totalElements").value(1));
  }

  @Test
  void listarActas_conSearchPorId_encuentraActa() throws Exception {
    Acta acta = crearActaEnviada();

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("search", String.valueOf(acta.getId()))
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(acta.getId()));
  }

  @Test
  void listarActas_conSearchSinCoincidencia_retornaVacio() throws Exception {
    crearActaEnviada();

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("search", "ZZZ-SIN-COINCIDENCIA")
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.meta.totalElements").value(0))
        .andExpect(jsonPath("$.meta.totalPages").value(0));
  }

  @Test
  void listarActas_ordenPorIdAsc_respetaSort() throws Exception {
    Acta primera = crearActaEnviada();
    Cargo otroCargo = cargoRepo.saveAndFlush(new Cargo("Prefecto/a", NivelCargo.PROVINCIA, 1));
    Acta segunda = crearActaEnviada(otroCargo);

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("sort", "id")
                .param("dir", "asc")
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.totalElements").value(2))
        .andExpect(jsonPath("$.data[0].id").value(primera.getId()))
        .andExpect(jsonPath("$.data[1].id").value(segunda.getId()));
  }

  @Test
  void listarActas_conParametrosInvalidos_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("page", "-1")
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("size", "0")
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("size", "101")
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("sort", "passwordHash")
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas")
                .param("dir", "sideways")
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void verActa_conIdValido_retornaActa() throws Exception {
    Acta acta = crearActaEnviada();

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas/{actaId}", acta.getId())
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(acta.getId()))
        .andExpect(jsonPath("$.estado").value("ENVIADA"));
  }

  @Test
  void disputar_actaEnviada_cambiaAEnDisputa() throws Exception {
    Acta acta = crearActaEnviada();

    String body = "{\"motivo\": \"Los votos no coinciden con el acta física\"}";

    mockMvc
        .perform(
            put("/api/v1/coordinator/actas/{actaId}/disputar", acta.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("EN_DISPUTA"));

    Acta actualizada = actaRepo.findById(acta.getId()).orElseThrow();
    assertThat(actualizada.getEstado()).isEqualTo(EstadoActa.EN_DISPUTA);
    assertThat(auditLogRepo.findByAccion("ACTA_EN_DISPUTA")).isNotEmpty();
  }

  @Test
  void disputar_sinMotivo_returns400() throws Exception {
    Acta acta = crearActaEnviada();

    mockMvc
        .perform(
            put("/api/v1/coordinator/actas/{actaId}/disputar", acta.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void corregir_actaEnDisputa_cambiaACorregida() throws Exception {
    Acta acta = crearActaEnviada();
    acta.setEstado(EstadoActa.EN_DISPUTA);
    acta = actaRepo.saveAndFlush(acta);

    mockMvc
        .perform(
            put("/api/v1/coordinator/actas/{actaId}/corregir", acta.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaCorreccionBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("CORREGIDA"))
        .andExpect(jsonPath("$.totalSufragantes").value(55));

    Acta actualizada = actaRepo.findById(acta.getId()).orElseThrow();
    assertThat(actualizada.getEstado()).isEqualTo(EstadoActa.CORREGIDA);
    assertThat(actualizada.getTotalSufragantes()).isEqualTo(55);
  }

  @Test
  void aprobar_actaEnviada_cambiaAAprobada() throws Exception {
    Acta acta = crearActaEnviada();

    mockMvc
        .perform(
            put("/api/v1/coordinator/actas/{actaId}/aprobar", acta.getId())
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("APROBADA"));

    Acta actualizada = actaRepo.findById(acta.getId()).orElseThrow();
    assertThat(actualizada.getEstado()).isEqualTo(EstadoActa.APROBADA);
    assertThat(auditLogRepo.findByAccion("ACTA_APROBADA")).isNotEmpty();
  }

  @Test
  void anular_asCoordinator_returns403() throws Exception {
    Acta acta = crearActaEnviada();
    acta.setEstado(EstadoActa.APROBADA);
    acta = actaRepo.saveAndFlush(acta);

    String body = "{\"motivo\": \"Error en recuento\"}";

    mockMvc
        .perform(
            delete("/api/v1/coordinator/actas/{actaId}", acta.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    Acta noAnulada = actaRepo.findById(acta.getId()).orElseThrow();
    assertThat(noAnulada.getEstado()).isEqualTo(EstadoActa.APROBADA);
  }

  @Test
  void anular_asMaster_cambiaAAnulada() throws Exception {
    Acta acta = crearActaEnviada();
    acta.setEstado(EstadoActa.APROBADA);
    acta = actaRepo.saveAndFlush(acta);

    String body = "{\"motivo\": \"Error oficial de recuento\"}";

    mockMvc
        .perform(
            delete("/api/v1/coordinator/actas/{actaId}", acta.getId())
                .header("Authorization", "Bearer " + masterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());

    Acta anulada = actaRepo.findById(acta.getId()).orElseThrow();
    assertThat(anulada.getEstado()).isEqualTo(EstadoActa.ANULADA);
    assertThat(anulada.getMotivoAnulacion()).isEqualTo("Error oficial de recuento");
    assertThat(auditLogRepo.findByAccion("ACTA_ANULADA")).isNotEmpty();
  }

  @Test
  void endpoints_sinToken_returns401() throws Exception {
    Acta acta = crearActaEnviada();

    mockMvc.perform(get("/api/v1/coordinator/actas")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(get("/api/v1/coordinator/actas/{actaId}", acta.getId()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void endpoints_asObserver_returns403() throws Exception {
    Acta acta = crearActaEnviada();

    mockMvc
        .perform(
            get("/api/v1/coordinator/actas").header("Authorization", "Bearer " + observerToken))
        .andExpect(status().isForbidden());
  }
}
