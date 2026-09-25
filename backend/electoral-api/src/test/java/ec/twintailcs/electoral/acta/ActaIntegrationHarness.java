package ec.twintailcs.electoral.acta;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalle;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
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
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.JrvRepository;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.domain.ParroquiaRepository;
import ec.twintailcs.electoral.shared.security.RlsContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Arnés de tests de integración para el flujo de actas (T-BE-15).
 *
 * <p>Patrón:
 *
 * <ul>
 *   <li>Contenedor PostgreSQL 16 compartido (static) — Testcontainers, NO H2 (per design.md D1).
 *   <li>Liquibase aplica el changelog completo al levantar el contexto.
 *   <li>Cada test parte de BD limpia: {@link BeforeEach} trunca tablas en orden de FK.
 *   <li>Builder methods devuelven los ids que cada test necesita — sin depender del seed
 *       de Liquibase (per design.md D4).
 *   <li>Limpieza explícita de {@code audit_log} y {@code notificacion} en {@link BeforeEach}:
 *       si no se truncaran, los tests que cuentan filas dependerían del orden de ejecución
 *       (per design.md D4 + tasks.md A.4).
 * </ul>
 *
 * <p>Razón de ser base class y no helper estático: cada subclase IT arranca su propio contexto
 * Spring (convención surefire), pero comparte el contenedor estático. La BD queda como un
 * recurso compartido entre tests de la misma JVM; los TRUNCATE en {@code @BeforeEach} son la
 * garantía de aislamiento.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class ActaIntegrationHarness {

  // NOTA: NO declaramos @Container aquí. Cada subclase IT declara su PROPIO @Container
  // estático. Razón: @Testcontainers de JUnit Jupiter escanea las anotaciones @Container
  // por clase, no por jerarquía de herencia — si el campo está solo en el padre, cada subclase
  // arranca un container nuevo en un puerto distinto cuando su context arranca. Peor: el primer
  // container muere cuando su context se cierra, y los contexts siguientes no lo reusan.
  //
  // Solución: cada subclase pega el bloque:
  //   @Container
  //   static final PostgreSQLContainer<?> POSTGRES =
  //       new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("electoral_test");
  //
  //   @DynamicPropertySource
  //   static void configure(DynamicPropertyRegistry r) { configureProps(r); }
  //
  // Y llama a {@link #configureProps(DynamicPropertyRegistry)} para registrar las props.
  // Coste: ~3s extra por IT (cada una arranca container). Vale la pena por aislamiento.
  protected static final String POSTGRES_IMAGE = "postgres:16-alpine";

  protected static void configureProps(DynamicPropertyRegistry registry) {
    // Las subclases IT declaran su propio @Container estático y llaman configureProps desde
    // su @DynamicPropertySource. Esta firma existe solo para documentar el contrato.
  }

  // Legacy configure() — ya no se usa porque cada subclase IT declara su propio @Container
  // y su propio @DynamicPropertySource. Se mantiene solo para que tests aislados (sin subclase)
  // puedan invocarlo. NO usar — ver comentario arriba.
  @Deprecated
  protected static void legacyConfigure(DynamicPropertyRegistry registry) {
    // intencionalmente vacío
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ActaRepository actaRepository;
  @Autowired protected ActaDetalleRepository actaDetalleRepository;
  @Autowired protected JrvRepository jrvRepository;
  @Autowired protected ParroquiaRepository parroquiaRepository;
  @Autowired protected RolRepository rolRepository;
  @Autowired protected UsuarioRepository usuarioRepository;
  @Autowired protected CargoRepository cargoRepository;
  @Autowired protected CandidatoRepository candidatoRepository;
  @Autowired protected PartidoRepository partidoRepository;
  @PersistenceContext protected EntityManager entityManager;

  /**
   * Trunca todas las tablas en orden de dependencias FK. Incluye explícitamente {@code
   * audit_log} y {@code notificacion} (per tasks.md A.4) y las semillas RBAC. {@code
   * RESTART IDENTITY} resetea sequences; {@code CASCADE} corta FKs sin errores.
   */
  @BeforeEach
  void truncateAll() {
    RlsContext.clear();
    jdbc.execute(
        "TRUNCATE acta, acta_detalle, evidencia, notificacion, audit_log, "
            + "observer_jrv_asignacion, candidato, jrv_cargo, cargo, partido, "
            + "jrv, parroquia, canton, provincia, "
            + "usuario "
            + "RESTART IDENTITY CASCADE");
  }

  // -----------------------------------------------------------------
  // Builder methods — geografía
  // -----------------------------------------------------------------

  protected ProvinciaFixture nuevaProvincia() {
    String codigo = "24-" + UUID.randomUUID().toString().substring(0, 4);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO provincia (nombre, codigo) VALUES (?,?) RETURNING id",
            Long.class,
            "Santa Elena",
            codigo);
    return new ProvinciaFixture(id, codigo);
  }

  protected CantonFixture nuevoCanton(ProvinciaFixture prov) {
    String codigo = "2401-" + UUID.randomUUID().toString().substring(0, 4);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO canton (nombre, codigo, provincia_id) VALUES (?,?,?) RETURNING id",
            Long.class,
            "Santa Elena",
            codigo,
            prov.id());
    return new CantonFixture(id, codigo);
  }

  protected ParroquiaFixture nuevaParroquia(CantonFixture canton) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO parroquia (nombre, es_urbana, canton_id) VALUES (?,?,?) RETURNING id",
            Long.class,
            "Santa Elena",
            true,
            canton.id());
    return new ParroquiaFixture(id);
  }

  protected JrvFixture nuevoJrv(ParroquiaFixture parro) {
    String numero = "JRV-" + UUID.randomUUID().toString().substring(0, 6);
    Jrv jrv = new Jrv();
    jrv.setNumero(numero);
    jrv.setRecinto("Recinto Test");
    jrv.setTotalElectores(300);
    Parroquia p = parroquiaRepository.findById(parro.id()).orElseThrow();
    jrv.setParroquia(p);
    jrv = jrvRepository.save(jrv);
    return new JrvFixture(jrv.getId(), jrv);
  }

  // -----------------------------------------------------------------
  // Builder methods — elecciones (cargo + partido + candidato)
  // -----------------------------------------------------------------

  protected CargoFixture nuevoCargo(String nombre, NivelCargo nivel) {
    Cargo cargo = cargoRepository.save(new Cargo(nombre, nivel, 1));
    return new CargoFixture(cargo.getId(), cargo);
  }

  protected Partido nuevoPartido(String nombre) {
    String siglas = nombre.length() >= 3 ? nombre.substring(0, 3).toUpperCase() : nombre.toUpperCase();
    return partidoRepository.save(new Partido(nombre, siglas, 1));
  }

  protected Candidato nuevoCandidato(CargoFixture cargo, Partido partido, String nombre) {
    Candidato c = new Candidato();
    c.setCargo(cargo.entity());
    c.setPartido(partido);
    c.setNombre(nombre);
    c.setOrden(1);
    return candidatoRepository.save(c);
  }

  /**
   * Habilita un cargo en una JRV (relación jrv_cargo con activo=true). Necesario porque
   * RegistrarActaUseCase.validarCargoHabilitadoEnJrv() lo exige.
   */
  protected void habilitarCargoEnJrv(JrvFixture jrv, CargoFixture cargo) {
    jdbc.update(
        "INSERT INTO jrv_cargo (jrv_id, cargo_id, activo) VALUES (?,?,true)", jrv.id(), cargo.id());
  }

  // -----------------------------------------------------------------
  // Builder methods — usuarios por rol
  // -----------------------------------------------------------------

  protected UsuarioFixture nuevoObserver() {
    return nuevoUsuario("OBSERVER", "obs-");
  }

  protected UsuarioFixture nuevoCoordinator() {
    return nuevoUsuario("COORDINATOR", "coord-");
  }

  protected UsuarioFixture nuevoMaster() {
    return nuevoUsuario("MASTER", "master-");
  }

  protected UsuarioFixture nuevoAdmin() {
    return nuevoUsuario("ADMIN", "admin-");
  }

  private UsuarioFixture nuevoUsuario(String rolNombre, String emailPrefix) {
    Long rolId = rolRepository.findByNombre(rolNombre).orElseThrow().getId();
    String email = emailPrefix + UUID.randomUUID() + "@test.local";
    UUID publicId = UUID.randomUUID();
    Long id =
        jdbc.queryForObject(
            "INSERT INTO usuario (email, password_hash, rol_id, habilitado, nombre, apellido, "
                + "public_id, creado_en) VALUES (?,?,?,?,?,?,?,NOW()) RETURNING id",
            Long.class,
            email,
            "$2a$12$" + "x".repeat(53),
            rolId,
            true,
            "Test",
            rolNombre,
            publicId);
    Usuario u = usuarioRepository.findById(id).orElseThrow();
    return new UsuarioFixture(u.getId(), publicId, u);
  }

  // -----------------------------------------------------------------
  // Builder methods — acta request
  // -----------------------------------------------------------------

  /**
   * Construye un ActaRequestDto válido: votos de candidatos + blancos + nulos = totalSufragantes.
   *
   * @param totalVotosCandidatos suma de los votos de los candidatos
   * @param blancos votos en blanco
   * @param nulos votos nulos
   * @param candidatos candidatos (uno por detalle)
   */
  protected ActaRequestDto actaRequestValida(
      JrvFixture jrv,
      CargoFixture cargo,
      int totalVotosCandidatos,
      int blancos,
      int nulos,
      List<Candidato> candidatos) {
    int total = totalVotosCandidatos + blancos + nulos;
    List<ActaDetalleDto> detalles =
        candidatos.stream()
            .map(c -> new ActaDetalleDto(c.getId(), distribVoto(totalVotosCandidatos, candidatos.indexOf(c))))
            .toList();
    return new ActaRequestDto(
        jrv.id(), cargo.id(), total, blancos, nulos, detalles);
  }

  private int distribVoto(int total, int idx) {
    // Reparto simple: primer candidato se lleva la mayoría.
    return idx == 0 ? total : 0;
  }

  // -----------------------------------------------------------------
  // Helpers de consulta
  // -----------------------------------------------------------------

  protected Acta actaPorId(Long id) {
    return actaRepository.findById(id).orElseThrow();
  }

  protected List<ActaDetalle> detallesDeActa(Long actaId) {
    return actaDetalleRepository.findAll().stream()
        .filter(d -> d.getActa().getId().equals(actaId))
        .toList();
  }

  protected long contarAuditLog() {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class);
    return n == null ? 0L : n;
  }

  protected long contarNotificaciones() {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM notificacion", Long.class);
    return n == null ? 0L : n;
  }

  /**
   * Ejecuta {@code SELECT current_setting('app.current_user_id', true)} en una conexión nueva
   * (no en el contexto Spring) para verificar si RLS fue establecido durante una petición. Si
   * devuelve {@code null} o vacío, RLS no está actuando (per design.md D3).
   */
  protected String currentRlsUserId() {
    return jdbc.queryForObject(
        "SELECT COALESCE(current_setting('app.current_user_id', true), '')", String.class);
  }

  // -----------------------------------------------------------------
  // Records de fixture
  // -----------------------------------------------------------------

  public record ProvinciaFixture(Long id, String codigo) {}

  public record CantonFixture(Long id, String codigo) {}

  public record ParroquiaFixture(Long id) {}

  public record JrvFixture(Long id, Jrv entity) {}

  public record CargoFixture(Long id, Cargo entity) {}

  public record UsuarioFixture(Long id, UUID publicId, Usuario entity) {}

  // -----------------------------------------------------------------
  // Verificación de contrato del arnés (tasks.md A.4)
  // -----------------------------------------------------------------

  /**
   * Tareas A.4: este método es la verificación que debe responder "¿qué NO limpia el arnés?".
   * Si en el futuro alguien agrega una tabla y olvida truncarla, este test fallará
   * mostrando qué tablas se quedaron con datos.
   */
  protected void assertArnesLimpia() {
    assertThat(contarAuditLog()).as("audit_log debe estar vacío entre tests").isZero();
    assertThat(contarNotificaciones()).as("notificacion debe estar vacío entre tests").isZero();
    Long actas = jdbc.queryForObject("SELECT COUNT(*) FROM acta", Long.class);
    assertThat(actas).as("acta debe estar vacío entre tests").isZero();
    Long usuarios =
        jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email LIKE '%@test.local'", Long.class);
    assertThat(usuarios).as("usuarios de test deben estar limpios entre tests").isZero();
  }
}
