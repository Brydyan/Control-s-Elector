package ec.twintailcs.electoral.acta.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.domain.ObserverJrvAsignacion;
import ec.twintailcs.electoral.acta.domain.ObserverJrvAsignacionRepository;
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
import ec.twintailcs.electoral.electoral.domain.JrvCargo;
import ec.twintailcs.electoral.electoral.domain.JrvCargoRepository;
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
 * Test de integracion HTTP end-to-end: ObserverController -> ActaService -> DB.
 *
 * <p>Patron migrado (T-BE-10): @SpringBootTest + Testcontainers + JWT real via JwtService.issue(),
 * autorizacion real via @PreAuthorize/@authz (no mocks de CurrentUser).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class ObserverControllerTest {

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

  @Autowired private ObserverJrvAsignacionRepository asignacionRepo;

  @Autowired private UsuarioRepository usuarioRepo;

  @Autowired private RolRepository rolRepo;

  @Autowired private AuditLogRepository auditLogRepo;

  @Autowired private JwtService jwtService;

  @Autowired private ProvinciaRepository provinciaRepo;

  @Autowired private CantonRepository cantonRepo;

  @Autowired private ParroquiaRepository parroquiaRepo;

  @Autowired private JrvRepository jrvRepo;

  @Autowired private CargoRepository cargoRepo;

  @Autowired private JrvCargoRepository jrvCargoRepo;

  @Autowired private PartidoRepository partidoRepo;

  @Autowired private CandidatoRepository candidatoRepo;

  private String observerToken;
  private String observerSinPermisoToken;
  private Usuario observer;
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

    observer = new Usuario();
    observer.setEmail("observer-" + UUID.randomUUID() + "@test.local");
    observer.setNombre("Observer");
    observer.setApellido("Test");
    observer.setPasswordHash("hash");
    observer.setRol(rolObserver);
    observer.setHabilitado(true);
    observer = usuarioRepo.saveAndFlush(observer);

    observerToken =
        jwtService
            .issue(
                observer.getPublicId(),
                observer.getEmail(),
                "OBSERVER",
                List.of("acta:upload"),
                "session-observer")
            .access();
    observerSinPermisoToken =
        jwtService
            .issue(
                observer.getPublicId(),
                observer.getEmail(),
                "OBSERVER",
                List.of(),
                "session-observer-sin-permiso")
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

    // ActaService.validarCargoHabilitadoEnJrv exige un JrvCargo activo para
    // el par (jrv, cargo); sin este insert, todos los POST /actas devuelven
    // 400 "El cargo X no está habilitado en la JRV Y". `activo` queda en true
    // por el default del constructor.
    jrvCargoRepo.saveAndFlush(new JrvCargo(jrv, cargo));

    Partido partidoA = partidoRepo.saveAndFlush(new Partido("Partido A", "PA", 1));
    Partido partidoB = partidoRepo.saveAndFlush(new Partido("Partido B", "PB", 2));

    candidatoA = candidatoRepo.saveAndFlush(new Candidato("Candidato A", 1, cargo, partidoA));
    candidatoB = candidatoRepo.saveAndFlush(new Candidato("Candidato B", 2, cargo, partidoB));

    asignacionRepo.saveAndFlush(new ObserverJrvAsignacion(observer, jrv));
  }

  private String actaValidaBody() {
    return """
            {
              "jrvId": %d, "cargoId": %d,
              "totalSufragantes": 50,
              "votosBlancos": 2, "votosNulos": 1,
              "detalles": [
                {"candidatoId": %d, "votos": 30},
                {"candidatoId": %d, "votos": 17}
              ]
            }
            """
        .formatted(jrv.getId(), cargo.getId(), candidatoA.getId(), candidatoB.getId());
  }

  private String actaVotosNoCuadranBody() {
    return """
            {
              "jrvId": %d, "cargoId": %d,
              "totalSufragantes": 50,
              "votosBlancos": 2, "votosNulos": 1,
              "detalles": [
                {"candidatoId": %d, "votos": 10},
                {"candidatoId": %d, "votos": 5}
              ]
            }
            """
        .formatted(jrv.getId(), cargo.getId(), candidatoA.getId(), candidatoB.getId());
  }

  @Test
  void registrarActa_conPayloadValido_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/observer/actas")
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaValidaBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.jrvId").value(jrv.getId()))
        .andExpect(jsonPath("$.cargoId").value(cargo.getId()))
        .andExpect(jsonPath("$.estado").value("ENVIADA"));

    Acta guardada = actaRepo.findByJrvIdAndCargoId(jrv.getId(), cargo.getId()).orElseThrow();
    assertThat(guardada.getTotalSufragantes()).isEqualTo(50);
    assertThat(auditLogRepo.findByAccion("REGISTRO_ACTA")).isNotEmpty();
  }

  @Test
  void registrarActa_conVotosQueNoCuadran_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/observer/actas")
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaVotosNoCuadranBody()))
        .andExpect(status().isBadRequest());

    assertThat(actaRepo.findByJrvIdAndCargoId(jrv.getId(), cargo.getId())).isEmpty();
  }

  @Test
  void registrarActa_sinPermisoActaUpload_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/observer/actas")
                .header("Authorization", "Bearer " + observerSinPermisoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaValidaBody()))
        .andExpect(status().isForbidden());

    assertThat(actaRepo.findByJrvIdAndCargoId(jrv.getId(), cargo.getId())).isEmpty();
  }

  @Test
  void registrarActa_duplicadaParaMismoJrvYCargo_returns409() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/observer/actas")
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaValidaBody()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/observer/actas")
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaValidaBody()))
        .andExpect(status().isConflict());
  }

  @Test
  void corregirActa_queNoExiste_returns400() throws Exception {
    // ActaService.corregir() lanza ActaInvalidaException (@ResponseStatus BAD_REQUEST)
    // cuando el actaId no existe -- no 404, ver ActaInvalidaException.
    mockMvc
        .perform(
            put("/api/v1/observer/actas/{actaId}/corregir", 999_999L)
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaValidaBody()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void corregirActa_queNoEstaEnDisputa_returns409() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/observer/actas")
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaValidaBody()))
        .andExpect(status().isCreated());

    Acta acta = actaRepo.findByJrvIdAndCargoId(jrv.getId(), cargo.getId()).orElseThrow();
    // Estado actual es ENVIADA (no EN_DISPUTA) -> ActaEstadoInvalidoException = CONFLICT (409)
    mockMvc
        .perform(
            put("/api/v1/observer/actas/{actaId}/corregir", acta.getId())
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaValidaBody()))
        .andExpect(status().isConflict());
  }

  @Test
  void corregirActa_propiaEnDisputa_returns200YQuedaCorregida() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/observer/actas")
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaValidaBody()))
        .andExpect(status().isCreated());

    Acta acta = actaRepo.findByJrvIdAndCargoId(jrv.getId(), cargo.getId()).orElseThrow();
    acta.setEstado(EstadoActa.EN_DISPUTA);
    actaRepo.saveAndFlush(acta);

    String corregidaBody =
        """
            {
              "jrvId": %d, "cargoId": %d,
              "totalSufragantes": 60,
              "votosBlancos": 3, "votosNulos": 2,
              "detalles": [
                {"candidatoId": %d, "votos": 35},
                {"candidatoId": %d, "votos": 20}
              ]
            }
            """
            .formatted(jrv.getId(), cargo.getId(), candidatoA.getId(), candidatoB.getId());

    mockMvc
        .perform(
            put("/api/v1/observer/actas/{actaId}/corregir", acta.getId())
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corregidaBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("CORREGIDA"))
        .andExpect(jsonPath("$.totalSufragantes").value(60));

    Acta actualizada = actaRepo.findById(acta.getId()).orElseThrow();
    assertThat(actualizada.getEstado()).isEqualTo(EstadoActa.CORREGIDA);
    assertThat(auditLogRepo.findByAccion("ACTA_CORREGIDA")).isNotEmpty();
  }

  @Test
  void misActas_devuelveSoloActasDelObserverAutenticado() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/observer/actas")
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaValidaBody()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/v1/observer/mis-actas").header("Authorization", "Bearer " + observerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].jrvId").value(jrv.getId()))
        .andExpect(jsonPath("$[0].estado").value("ENVIADA"));
  }

  @Test
  void misActas_sinToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/observer/mis-actas")).andExpect(status().isUnauthorized());
  }

  @Test
  void registrarActa_observerSinAsignacionPrevia_puedeRegistrarEnCualquierJrv() throws Exception {
    // Fix CRITICAL #1 (2026-08-14): OBSERVER selecciona JRV libremente,
    // sin pre-asignacion en ObserverJrvAsignacion. Se crea una JRV nueva
    // (jrvLibre) para la cual el observer NUNCA tuvo asignacion --
    // registrar debe funcionar igual (201), no 403.
    Parroquia parroquia = jrv.getParroquia();
    Jrv jrvLibre = new Jrv();
    jrvLibre.setNumero("JRV-LIBRE-" + UUID.randomUUID().toString().substring(0, 8));
    jrvLibre.setRecinto("Escuela Sin Asignacion");
    jrvLibre.setTotalElectores(300);
    jrvLibre.setParroquia(parroquia);
    jrvLibre = jrvRepo.saveAndFlush(jrvLibre);
    jrvCargoRepo.saveAndFlush(new JrvCargo(jrvLibre, cargo));

    assertThat(asignacionRepo.findByJrv(jrvLibre)).isEmpty();

    String bodySinAsignacion =
        """
            {
              "jrvId": %d, "cargoId": %d,
              "totalSufragantes": 50,
              "votosBlancos": 2, "votosNulos": 1,
              "detalles": [
                {"candidatoId": %d, "votos": 30},
                {"candidatoId": %d, "votos": 17}
              ]
            }
            """
            .formatted(jrvLibre.getId(), cargo.getId(), candidatoA.getId(), candidatoB.getId());

    mockMvc
        .perform(
            post("/api/v1/observer/actas")
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodySinAsignacion))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.jrvId").value(jrvLibre.getId()))
        .andExpect(jsonPath("$.estado").value("ENVIADA"));

    Acta guardada = actaRepo.findByJrvIdAndCargoId(jrvLibre.getId(), cargo.getId()).orElseThrow();
    assertThat(guardada.getRegistradaPor().getId()).isEqualTo(observer.getId());
  }

  @Test
  void correccionesPendientes_devuelveSoloActasEnDisputa() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/observer/actas")
                .header("Authorization", "Bearer " + observerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaValidaBody()))
        .andExpect(status().isCreated());

    Acta acta = actaRepo.findByJrvIdAndCargoId(jrv.getId(), cargo.getId()).orElseThrow();
    acta.setEstado(EstadoActa.EN_DISPUTA);
    actaRepo.saveAndFlush(acta);

    mockMvc
        .perform(
            get("/api/v1/observer/correcciones-pendientes")
                .header("Authorization", "Bearer " + observerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].estado").value("EN_DISPUTA"))
        .andExpect(jsonPath("$[0].jrvId").value(jrv.getId()));
  }
}
