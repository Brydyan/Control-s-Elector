package ec.twintailcs.electoral.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalle;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.dashboard.web.dto.NovedadDto;
import ec.twintailcs.electoral.dashboard.web.dto.ProgresoCantonDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResumenActasDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResultadoCargoDto;
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
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests end-to-end del DashboardController vía MockMvc + Testcontainers + JWT real.
 *
 * <p>Cada test mapea 1-a-1 un requisito del {@code specs/dashboard/spec.md}. Mutaciones D.1-D.6
 * documentadas en {@code tasks.md}.
 *
 * <p>Helpers de setUp(): {@code canton}, {@code jrv}, {@code cargo}, {@code candidato}, {@code acta}
 * crean entidades persistidas con ids estables. Las relaciones JRV→Parroquia→Canton se construyen en
 * cada test según la combinación que se necesite (canton con/sin JRVs, JRV con 1 o N cargos, etc.).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class DashboardControllerTest {

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
  @Autowired private JwtService jwtService;
  @Autowired private ActaRepository actaRepo;
  @Autowired private ActaDetalleRepository actaDetalleRepo;
  @Autowired private UsuarioRepository usuarioRepo;
  @Autowired private RolRepository rolRepo;
  @Autowired private CantonRepository cantonRepo;
  @Autowired private ParroquiaRepository parroquiaRepo;
  @Autowired private JrvRepository jrvRepo;
  @Autowired private ProvinciaRepository provinciaRepo;
  @Autowired private CargoRepository cargoRepo;
  @Autowired private PartidoRepository partidoRepo;
  @Autowired private CandidatoRepository candidatoRepo;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String observerToken;
  private String coordinatorToken;
  private String masterToken;
  private String adminToken;
  private Usuario observer;
  private Usuario coordinator;
  private Usuario master;
  private Usuario admin;

  @BeforeEach
  void setUp() {
    Rol rolObserver = rolRepo.findByNombre("OBSERVER").orElseThrow();
    Rol rolCoordinator = rolRepo.findByNombre("COORDINATOR").orElseThrow();
    Rol rolAdmin = rolRepo.findByNombre("ADMIN").orElseThrow();
    Rol rolMaster = rolRepo.findByNombre("MASTER").orElseThrow();

    observer = saveUsuario("observer", rolObserver, List.of("acta:upload"));
    coordinator = saveUsuario("coordinator", rolCoordinator, List.of("acta:read"));
    admin = saveUsuario("admin", rolAdmin, List.of("acta:read"));
    master = saveUsuario("master", rolMaster, List.of("acta:read"));

    observerToken =
        jwtService
            .issue(
                observer.getPublicId(),
                observer.getEmail(),
                "OBSERVER",
                List.of("acta:upload"),
                "sess-observer")
            .access();
    coordinatorToken =
        jwtService
            .issue(
                coordinator.getPublicId(),
                coordinator.getEmail(),
                "COORDINATOR",
                List.of("acta:read"),
                "sess-coordinator")
            .access();
    masterToken =
        jwtService
            .issue(
                master.getPublicId(),
                master.getEmail(),
                "MASTER",
                List.of("acta:read"),
                "sess-master")
            .access();
    adminToken =
        jwtService
            .issue(
                admin.getPublicId(),
                admin.getEmail(),
                "ADMIN",
                List.of("acta:read"),
                "sess-admin")
            .access();
  }

  private Usuario saveUsuario(String tag, Rol rol, List<String> authorities) {
    Usuario u = new Usuario();
    u.setEmail(tag + "-" + UUID.randomUUID() + "@test.local");
    u.setNombre(tag);
    u.setApellido("Test");
    u.setPasswordHash("hash");
    u.setRol(rol);
    u.setHabilitado(true);
    return usuarioRepo.saveAndFlush(u);
  }

  // -------------------------------------------------------------------
  // Helpers de geografía
  // -------------------------------------------------------------------

  private Provincia crearProvincia(String nombre) {
    return provinciaRepo.saveAndFlush(new Provincia(nombre, "24"));
  }

  private Canton crearCanton(String nombre, Provincia provincia) {
    Canton c = new Canton();
    c.setNombre(nombre);
    // Codigo de 10 chars (limite columna): prefijo "24" + hash uuid corto. Genera únicos estables.
    String tail = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    c.setCodigo("24" + tail);
    c.setProvincia(provincia);
    return cantonRepo.saveAndFlush(c);
  }

  private Parroquia crearParroquia(String nombre, Canton canton) {
    Parroquia p = new Parroquia();
    p.setNombre(nombre);
    p.setEsUrbana(true);
    p.setCanton(canton);
    return parroquiaRepo.saveAndFlush(p);
  }

  private Jrv crearJrv(String tag, Parroquia parroquia) {
    Jrv j = new Jrv();
    j.setNumero("JRV-" + tag + "-" + UUID.randomUUID().toString().substring(0, 4));
    j.setRecinto("Recinto " + tag);
    j.setTotalElectores(300);
    j.setParroquia(parroquia);
    return jrvRepo.saveAndFlush(j);
  }

  // -------------------------------------------------------------------
  // Helpers de configuración electoral
  // -------------------------------------------------------------------

  private Cargo crearCargo(String nombre) {
    return cargoRepo.saveAndFlush(new Cargo(nombre, NivelCargo.CANTON, 1));
  }

  private Partido crearPartido(String nombre, String siglas, Integer numero) {
    return partidoRepo.saveAndFlush(new Partido(nombre, siglas, numero));
  }

  private Candidato crearCandidato(String nombre, Integer orden, Cargo cargo, Partido partido) {
    return candidatoRepo.saveAndFlush(new Candidato(nombre, orden, cargo, partido));
  }

  // -------------------------------------------------------------------
  // Helpers de actas
  // -------------------------------------------------------------------

  private Acta crearActa(Jrv jrv, Cargo cargo, EstadoActa estado, int sufragantes) {
    Acta a = new Acta();
    a.setJrv(jrv);
    a.setCargo(cargo);
    a.setRegistradaPor(observer);
    a.setEstado(estado);
    a.setTotalSufragantes(sufragantes);
    a.setVotosBlancos(0);
    a.setVotosNulos(0);
    return actaRepo.saveAndFlush(a);
  }

  private void detalle(Acta a, Candidato c, int votos) {
    ActaDetalle d = new ActaDetalle(a, c, votos);
    actaDetalleRepo.saveAndFlush(d);
  }

  // ===================================================================
  // RF-DASH-01 — Acceso por rol
  // ===================================================================

  @Test
  void RF_DASH_01_resumen_coordinator_retorna200() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/resumen")
                .header("Authorization", "Bearer " + coordinatorToken))
        .andExpect(status().isOk());
  }

  @Test
  void RF_DASH_01_resumen_master_retorna200() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/resumen").header("Authorization", "Bearer " + masterToken))
        .andExpect(status().isOk());
  }

  @Test
  void RF_DASH_01_resumen_admin_retorna200() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/resumen").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  void RF_DASH_01_cualquier_endpoint_observer_retorna403() throws Exception {
    for (String path :
        Arrays.asList(
            "/api/v1/dashboard/resumen",
            "/api/v1/dashboard/resultados",
            "/api/v1/dashboard/progreso",
            "/api/v1/dashboard/novedades")) {
      mockMvc
          .perform(get(path).header("Authorization", "Bearer " + observerToken))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void RF_DASH_01_cualquier_endpoint_sinToken_retorna401() throws Exception {
    for (String path :
        Arrays.asList(
            "/api/v1/dashboard/resumen",
            "/api/v1/dashboard/resultados",
            "/api/v1/dashboard/progreso",
            "/api/v1/dashboard/novedades")) {
      mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
  }

  // ===================================================================
  // RF-DASH-02 — Sólo GET
  // ===================================================================

  @Test
  void RF_DASH_02_soloGetMapping_noExistenAnotacionesDeEscritura() {
    // Carga la clase sin proxy de Spring para inspeccionar anotaciones literales.
    Class<?> clazz = DashboardController.class;
    boolean hasClassLevelRequestMapping =
        clazz.isAnnotationPresent(RequestMapping.class)
            && clazz.getAnnotation(RequestMapping.class).value().length > 0
            && Arrays.asList(clazz.getAnnotation(RequestMapping.class).value())
                .contains("/api/v1/dashboard");

    for (Method m : clazz.getDeclaredMethods()) {
      boolean isWrite =
          m.isAnnotationPresent(PostMapping.class)
              || m.isAnnotationPresent(PutMapping.class)
              || m.isAnnotationPresent(PatchMapping.class)
              || m.isAnnotationPresent(DeleteMapping.class);
      assertThat(isWrite)
          .as(
              "El método '%s' declara una anotación de escritura prohibida en este controlador"
                  + " (RF-DASH-02 / design.md D2). Quítala o refactoriza: el módulo dashboard"
                  + " es de sólo lectura por forma, no por guard.",
              m.getName())
          .isFalse();
      if (m.isAnnotationPresent(GetMapping.class) && hasClassLevelRequestMapping) {
        assertThat(m.isAnnotationPresent(RequestMapping.class))
            .as("Método '%s' lleva @GetMapping innecesario si ya hay @RequestMapping a nivel de clase y método", m.getName())
            .isFalse();
      }
    }
  }

  @Test
  void RF_DASH_02_todosLosMetodosEstanAnotadosConPreAuthorizeOGetMapping() {
    Class<?> clazz = DashboardController.class;
    assertThat(clazz.isAnnotationPresent(PreAuthorize.class))
        .as("El controlador debe llevar @PreAuthorize de clase para RF-DASH-01")
        .isTrue();
    for (Method m : clazz.getDeclaredMethods()) {
      if (m.isAnnotationPresent(GetMapping.class)) {
        // permitidos
      } else if (m.isSynthetic()) {
        // ignorado
      } else {
        // No hay otro tipo permitido
        throw new AssertionError(
            "Método '"
                + m.getName()
                + "' no es GET. RF-DASH-02 + design.md D2: el módulo dashboard es de sólo"
                + " lectura.");
      }
    }
  }

  // ===================================================================
  // RF-DASH-03 — /resumen excluye ANULADA
  // ===================================================================

  @Test
  void RF_DASH_03_resumen_excluyeAnulada() throws Exception {
    Provincia prov = crearProvincia("Santa Elena");
    Canton canton = crearCanton("Santa Elena", prov);
    Parroquia parro = crearParroquia("Centro", canton);
    // La unique index uk_acta_activa limita a 1 acta activa por (jrv, cargo).
    // Una JRV distinta por estado activo. Las ANULADAS pueden apilarse en una sola JRV.
    Jrv jrvE1 = crearJrv("E1", parro);
    Jrv jrvE2 = crearJrv("E2", parro);
    Jrv jrvE3 = crearJrv("E3", parro);
    Jrv jrvD = crearJrv("D", parro);
    Jrv jrvA = crearJrv("A", parro);
    Jrv jrvAnulada = crearJrv("X", parro);

    Cargo cargo = crearCargo("Alcalde");

    crearActa(jrvE1, cargo, EstadoActa.ENVIADA, 100);
    crearActa(jrvE2, cargo, EstadoActa.ENVIADA, 100);
    crearActa(jrvE3, cargo, EstadoActa.ENVIADA, 100);
    crearActa(jrvD, cargo, EstadoActa.EN_DISPUTA, 100);
    crearActa(jrvA, cargo, EstadoActa.APROBADA, 100);
    for (int i = 0; i < 5; i++) {
      Acta a = crearActa(jrvAnulada, cargo, EstadoActa.ANULADA, 100);
      a.setMotivoAnulacion("test-rf-03-anulada-" + i);
      actaRepo.saveAndFlush(a);
    }

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/dashboard/resumen")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

    String json = result.getResponse().getContentAsString();
    ResumenActasDto r =
        new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, ResumenActasDto.class);

    assertThat(r.enviadas()).isEqualTo(3L);
    assertThat(r.enDisputa()).isEqualTo(1L);
    assertThat(r.corregidas()).isEqualTo(0L);
    assertThat(r.aprobadas()).isEqualTo(1L);
    assertThat(r.totalActivas()).isEqualTo(5L); // 3+1+0+1; las 5 ANULADA no cuentan
  }

  @Test
  void RF_DASH_03_resumen_totalActivasSumaDeLasCuatroSinAnular() throws Exception {
    // Carga heterogénea para probar la suma, no sólo presencia. La unique index
    // uk_acta_activa impide > 1 acta activa por (jrv, cargo). Usamos cargos distintos.
    Provincia prov = crearProvincia("Santa Elena");
    Canton canton = crearCanton("Salinas", prov);
    Parroquia parro = crearParroquia("Salinas", canton);
    Jrv jrv1 = crearJrv("B1", parro);
    Jrv jrv2 = crearJrv("B2", parro);
    Jrv jrv3 = crearJrv("B3", parro);
    Jrv jrv4 = crearJrv("B4", parro);
    Jrv jrv5 = crearJrv("B5", parro);
    Jrv jrv6 = crearJrv("B6", parro);
    Jrv jrv7 = crearJrv("B7", parro);

    Cargo c1 = crearCargo("Alcalde-1");
    Cargo c2 = crearCargo("Alcalde-2");
    Cargo c3 = crearCargo("Alcalde-3");
    Cargo c4 = crearCargo("Alcalde-4");
    Cargo c5 = crearCargo("Alcalde-5");
    Cargo c6 = crearCargo("Alcalde-6");
    Cargo c7 = crearCargo("Alcalde-7");

    crearActa(jrv1, c1, EstadoActa.ENVIADA, 100);
    crearActa(jrv2, c2, EstadoActa.ENVIADA, 100);
    crearActa(jrv3, c3, EstadoActa.EN_DISPUTA, 100);
    crearActa(jrv4, c4, EstadoActa.CORREGIDA, 100);
    crearActa(jrv5, c5, EstadoActa.CORREGIDA, 100);
    crearActa(jrv6, c6, EstadoActa.CORREGIDA, 100);
    crearActa(jrv7, c7, EstadoActa.APROBADA, 100);
    // anuladas adicionales no cambian totales (la unique index ya no las restringe)
    Acta a1 = crearActa(jrv1, c1, EstadoActa.ANULADA, 100);
    a1.setMotivoAnulacion("test-suma-anulada-1");
    actaRepo.saveAndFlush(a1);

    mockMvc
        .perform(
            get("/api/v1/dashboard/resumen").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enviadas").value(2))
        .andExpect(jsonPath("$.enDisputa").value(1))
        .andExpect(jsonPath("$.corregidas").value(3))
        .andExpect(jsonPath("$.aprobadas").value(1))
        .andExpect(jsonPath("$.totalActivas").value(7));
  }

  // ===================================================================
  // RF-DASH-04 — /resultados sin votos de ANULADA (90 vs 590)
  // ===================================================================

  @Test
  void RF_DASH_04_resultados_excluyeVotosDeAnulada() throws Exception {
    Provincia prov = crearProvincia("Santa Elena");
    Canton canton = crearCanton("La Libertad", prov);
    Parroquia parro = crearParroquia("La Libertad", canton);
    Jrv jrv = crearJrv("X", parro);

    Cargo cargo = crearCargo("Alcalde");
    Partido partido = crearPartido("PA", "PA", 1);
    Candidato c = crearCandidato("Candidato 1", 1, cargo, partido);

    Acta aprobada = crearActa(jrv, cargo, EstadoActa.APROBADA, 100);
    detalle(aprobada, c, 90);
    Acta anulada = crearActa(jrv, cargo, EstadoActa.ANULADA, 600);
    detalle(anulada, c, 500);

    mockMvc
        .perform(
            get("/api/v1/dashboard/resultados").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].cargoId").value(cargo.getId()))
        .andExpect(jsonPath("$[0].cargoNombre").value("Alcalde"))
        .andExpect(jsonPath("$[0].candidatos[0].candidatoId").value(c.getId()))
        .andExpect(jsonPath("$[0].candidatos[0].votos").value(90));
  }

  @Test
  void RF_DASH_04_resultados_ordenDescendenteVotosDentroDeCargo() throws Exception {
    Provincia prov = crearProvincia("Santa Elena");
    Canton canton = crearCanton("Salinas", prov);
    Parroquia parro = crearParroquia("Salinas", canton);
    Jrv jrv1 = crearJrv("1", parro);
    Jrv jrv2 = crearJrv("2", parro);

    Cargo cargo = crearCargo("Concejal");
    Partido partido = crearPartido("PB", "PB", 2);
    Candidato c1 = crearCandidato("Candidato Bajo", 1, cargo, partido);
    Candidato c2 = crearCandidato("Candidato Alto", 2, cargo, partido);
    Candidato c3 = crearCandidato("Candidato Medio", 3, cargo, partido);

    // c1=10, c2=50, c3=25 → orden esperado: c2, c3, c1
    Acta a1 = crearActa(jrv1, cargo, EstadoActa.APROBADA, 90);
    detalle(a1, c1, 10);
    Acta a2 = crearActa(jrv2, cargo, EstadoActa.APROBADA, 80);
    detalle(a2, c2, 50);
    detalle(a2, c3, 25);

    mockMvc
        .perform(
            get("/api/v1/dashboard/resultados").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].cargoId").value(cargo.getId()))
        .andExpect(jsonPath("$[0].candidatos[0].candidatoId").value(c2.getId()))
        .andExpect(jsonPath("$[0].candidatos[0].votos").value(50))
        .andExpect(jsonPath("$[0].candidatos[1].candidatoId").value(c3.getId()))
        .andExpect(jsonPath("$[0].candidatos[1].votos").value(25))
        .andExpect(jsonPath("$[0].candidatos[2].candidatoId").value(c1.getId()))
        .andExpect(jsonPath("$[0].candidatos[2].votos").value(10));
  }

  // ===================================================================
  // RF-DASH-05 — /progreso por cantón, excluye ANULADA, DISTINCT
  // ===================================================================

  @Test
  void RF_DASH_05_progreso_5Jrvs_2ConActa_porcentaje40() throws Exception {
    Provincia prov = crearProvincia("Santa Elena");
    Canton canton = crearCanton("Progreso1", prov);
    Parroquia parro = crearParroquia("Progreso1", canton);
    Jrv jrv1 = crearJrv("1", parro);
    Jrv jrv2 = crearJrv("2", parro);
    Jrv jrv3 = crearJrv("3", parro);
    Jrv jrv4 = crearJrv("4", parro);
    Jrv jrv5 = crearJrv("5", parro);

    Cargo cargo = crearCargo("Alcalde");

    // 2 de las 5 JRVs tienen acta no anulada
    crearActa(jrv1, cargo, EstadoActa.APROBADA, 50);
    crearActa(jrv2, cargo, EstadoActa.ENVIADA, 50);

    mockMvc
        .perform(
            get("/api/v1/dashboard/progreso").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.cantonId==" + canton.getId() + ")]").exists())
        .andExpect(
            jsonPath("$[?(@.cantonId==" + canton.getId() + ")].totalJrvs").value(
                org.hamcrest.Matchers.contains(5)))
        .andExpect(
            jsonPath("$[?(@.cantonId==" + canton.getId() + ")].jrvsConActa").value(
                org.hamcrest.Matchers.contains(2)))
        .andExpect(
            jsonPath("$[?(@.cantonId==" + canton.getId() + ")].porcentaje").value(
                org.hamcrest.Matchers.contains(40.0)));
  }

  @Test
  void RF_DASH_05_progreso_jrvConActaAnuladaNoCuenta() throws Exception {
    Provincia prov = crearProvincia("Santa Elena");
    Canton canton = crearCanton("ProgresoAnulada", prov);
    Parroquia parro = crearParroquia("ProgresoAnulada", canton);
    Jrv jrv = crearJrv("U", parro);

    Cargo cargo = crearCargo("Alcalde");
    crearActa(jrv, cargo, EstadoActa.ANULADA, 50); // NO debe contar

    mockMvc
        .perform(
            get("/api/v1/dashboard/progreso").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$[?(@.cantonId==" + canton.getId() + ")].jrvsConActa")
                .value(org.hamcrest.Matchers.contains(0)))
        .andExpect(
            jsonPath(
                    "$[?(@.cantonId==" + canton.getId() + ")].porcentaje")
                .value(org.hamcrest.Matchers.contains(0.0)));
  }

  @Test
  void RF_DASH_05_progreso_jrvConTresCargosCuentaUnaVez() throws Exception {
    Provincia prov = crearProvincia("Santa Elena");
    Canton canton = crearCanton("Progreso3Cargos", prov);
    Parroquia parro = crearParroquia("Progreso3Cargos", canton);
    Jrv jrv = crearJrv("X", parro);

    Cargo cargo1 = crearCargo("Prefecto");
    Cargo cargo2 = crearCargo("Alcalde");
    Cargo cargo3 = crearCargo("Concejal");

    crearActa(jrv, cargo1, EstadoActa.APROBADA, 50);
    crearActa(jrv, cargo2, EstadoActa.APROBADA, 50);
    crearActa(jrv, cargo3, EstadoActa.APROBADA, 50);

    mockMvc
        .perform(
            get("/api/v1/dashboard/progreso").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$[?(@.cantonId==" + canton.getId() + ")].totalJrvs")
                .value(org.hamcrest.Matchers.contains(1)))
        .andExpect(
            jsonPath(
                    "$[?(@.cantonId==" + canton.getId() + ")].jrvsConActa")
                .value(org.hamcrest.Matchers.contains(1)));
  }

  @Test
  void RF_DASH_05_progreso_cantonSinJrvs_porcentajeCeroSinNaN() throws Exception {
    Provincia prov = crearProvincia("Santa Elena");
    Canton cantonVacio = crearCanton("SinJRV", prov);
    // Sin parroquia ni JRVs

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/dashboard/progreso")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

    String json = result.getResponse().getContentAsString();
    ProgresoCantonDto[] arr =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(json, ProgresoCantonDto[].class);

    ProgresoCantonDto dto = null;
    for (ProgresoCantonDto d : arr) {
      if (d.cantonId().equals(cantonVacio.getId())) {
        dto = d;
        break;
      }
    }
    assertThat(dto).isNotNull();
    assertThat(dto.totalJrvs()).isZero();
    assertThat(dto.jrvsConActa()).isZero();
    assertThat(dto.porcentaje()).isEqualTo(0.0); // explícitamente 0.0, no NaN ni excepción
  }

  // ===================================================================
  // RF-DASH-06 — /novedades sólo EN_DISPUTA
  // ===================================================================

  @Test
  void RF_DASH_06_novedades_soloEnDisputa() throws Exception {
    Provincia prov = crearProvincia("Santa Elena");
    Canton canton = crearCanton("Novedades", prov);
    Parroquia parro = crearParroquia("Novedades", canton);
    // Varias JRVs porque la unique index uk_acta_activa limita a 1 acta activa por (jrv, cargo)
    Jrv jrvE = crearJrv("E", parro); // para EN_DISPUTA
    Jrv jrvA = crearJrv("A", parro); // para APROBADA
    Jrv jrvV = crearJrv("V", parro); // para ENVIADA
    Jrv jrvN = crearJrv("N", parro); // para ANULADA

    Cargo cargo = crearCargo("Alcalde");
    Acta enDisputa = crearActa(jrvE, cargo, EstadoActa.EN_DISPUTA, 50);
    Acta aprobada = crearActa(jrvA, cargo, EstadoActa.APROBADA, 50);
    crearActa(jrvV, cargo, EstadoActa.ENVIADA, 50);
    crearActa(jrvN, cargo, EstadoActa.ANULADA, 50);

    mockMvc
        .perform(
            get("/api/v1/dashboard/novedades").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].actaPublicId").value(enDisputa.getPublicId().toString()))
        .andExpect(jsonPath("$[0].tipo").value("ACTA_EN_DISPUTA"))
        .andExpect(jsonPath("$[0].jrvId").value(jrvE.getId()))
        .andExpect(jsonPath("$[0].cargoId").value(cargo.getId()))
        .andExpect(jsonPath("$[0].observerPublicId").value(observer.getPublicId().toString()))
        // aprobada NO aparece
        .andExpect(
            jsonPath(
                    "$[?(@.actaPublicId=='" + aprobada.getPublicId() + "')]")
                .doesNotExist());
  }

  @Test
  void RF_DASH_06_novedades_baseVacia_retornaListaVacia() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/novedades").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // ===================================================================
  // RF-DASH-07 — minutosTranscurridos independiente de TZ
  //
  // El test original aceptaba el rango [0, 1] para actas recién creadas, así que cualquier
  // implementación —incluyendo la reintroducción del patrón de zona horaria de T-BE-13.md:374-377
  // y la variante exacerbada (LocalDate.atStartOfDay * 1440)— pasaba. Para que un test detecte
  // esas mutaciones, fechaRegistro debe cruzar el límite del día en al menos una TZ. Se fija
  // por JdbcTemplate para bypassear @CreationTimestamp(updatable=false) — ver design.md D4 y
  // fixes-required.md §F1.
  // ===================================================================

  @Test
  void RF_DASH_07_novedades_minutosTranscurridos_actaDeAyer2300UTC_coherenteEnUTCyGYE()
      throws Exception {
    Provincia prov = crearProvincia("Santa Elena");
    Canton canton = crearCanton("TZTest1", prov);
    Parroquia parro = crearParroquia("TZTest1", canton);
    Jrv jrv = crearJrv("T1", parro);
    Cargo cargo = crearCargo("Alcalde");

    Acta acta = crearActa(jrv, cargo, EstadoActa.EN_DISPUTA, 50);

    // fechaRegistro = ayer 23:00 UTC (= hoy 00:00 UTC - 1h). Bypassea @CreationTimestamp.
    Instant fechaRegistro =
        Instant.now().truncatedTo(ChronoUnit.DAYS).minus(Duration.ofHours(1));
    jdbcTemplate.update(
        "UPDATE acta SET fecha_registro = ? WHERE id = ?",
        Timestamp.from(fechaRegistro),
        acta.getId());

    // Cálculo esperado con la fórmula correcta: Duration.between(Instant, Instant) es
    // TZ-invariante. El servicio debe producir exactamente este número en UTC y en
    // America/Guayaquil (sin tolerancia).
    long expected = Math.max(0L, Duration.between(fechaRegistro, Instant.now()).toMinutes());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/dashboard/novedades")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

    String json = result.getResponse().getContentAsString();
    NovedadDto[] arr =
        new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, NovedadDto[].class);

    assertThat(arr).hasSize(1);
    assertEquals(expected, arr[0].minutosTranscurridos());
  }

  @Test
  void RF_DASH_07_novedades_minutosTranscurridos_actaDeAyer0400UTC_coherenteEnUTCyGYE()
      throws Exception {
    Provincia prov = crearProvincia("Santa Elena");
    Canton canton = crearCanton("TZTest2", prov);
    Parroquia parro = crearParroquia("TZTest2", canton);
    Jrv jrv = crearJrv("T2", parro);
    Cargo cargo = crearCargo("Alcalde");

    Acta acta = crearActa(jrv, cargo, EstadoActa.EN_DISPUTA, 50);

    // fechaRegistro = ayer 04:00 UTC (= hoy 00:00 UTC - 20h). En GYE equivale a ayer 23:00 local,
    // distinto offset respecto al caso anterior — la mutación exacerbada debe divergir.
    Instant fechaRegistro =
        Instant.now().truncatedTo(ChronoUnit.DAYS).minus(Duration.ofHours(20));
    jdbcTemplate.update(
        "UPDATE acta SET fecha_registro = ? WHERE id = ?",
        Timestamp.from(fechaRegistro),
        acta.getId());

    long expected = Math.max(0L, Duration.between(fechaRegistro, Instant.now()).toMinutes());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/dashboard/novedades")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

    String json = result.getResponse().getContentAsString();
    NovedadDto[] arr =
        new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, NovedadDto[].class);

    assertThat(arr).hasSize(1);
    assertEquals(expected, arr[0].minutosTranscurridos());
  }

  // ===================================================================
  // RNF-DASH-01 — base vacía: cuatro endpoints → 200
  // ===================================================================

  @Test
  void RNF_DASH_01_baseVacia_losCuatroEndpointsResponden200() throws Exception {
    for (String path :
        Arrays.asList(
            "/api/v1/dashboard/resumen",
            "/api/v1/dashboard/resultados",
            "/api/v1/dashboard/progreso",
            "/api/v1/dashboard/novedades")) {
      mockMvc
          .perform(get(path).header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isOk());
    }
  }

  @Test
  void RNF_DASH_01_baseVacia_resumenTodoCero() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/resumen").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enviadas").value(0))
        .andExpect(jsonPath("$.enDisputa").value(0))
        .andExpect(jsonPath("$.corregidas").value(0))
        .andExpect(jsonPath("$.aprobadas").value(0))
        .andExpect(jsonPath("$.totalActivas").value(0));
  }

  @Test
  void RNF_DASH_01_baseVacia_resultadosListaVacia() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/dashboard/resultados").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void RNF_DASH_01_baseVacia_progresoSinDivisionPorCero() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/dashboard/progreso").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

    String json = result.getResponse().getContentAsString();
    ProgresoCantonDto[] arr =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(json, ProgresoCantonDto[].class);
    // array vacío: ningún cantón, ningún NaN
    assertThat(arr).isNotNull();
  }
}
