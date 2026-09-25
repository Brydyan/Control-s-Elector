package ec.twintailcs.electoral.acta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import ec.twintailcs.electoral.electoral.domain.Partido;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RF-TEST-02 — Invariante de votos llega hasta el HTTP, en registrar Y corregir.
 *
 * <p>Aserciones:
 *
 * <ul>
 *   <li>Si {@code SUM(detalles.votos) + blancos + nulos != totalSufragantes}, el endpoint
 *       rechaza con ActaInvalidaException y NO crea fila en {@code acta} ni en
 *       {@code acta_detalle}.
 *   <li>Si la suma cuadra, se crea el acta (201).
 *   <li>Misma regla en {@code corregir}: el path {@code PUT /actas/{id}/corregir} rechaza si
 *       la suma no cuadra.
 * </ul>
 *
 * <p>Mutación C.2: invertir el {@code !=} en {@code validarSumaVotosDto} de {@code
 * RegistrarActaUseCase}. Debe caer el test de suma inválida en registrar. Repetir en {@code
 * CorregirActaUseCase} — la regla vive en dos sitios (per tasks.md C.2 + spec §4.2).
 */
class ActaInvarianteIT extends ActaIntegrationHarness {
  @Container
  static final org.testcontainers.containers.PostgreSQLContainer<?> POSTGRES =
      new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("electoral_test");

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.liquibase.enabled", () -> "true");
  }


  @Autowired private ActaService actaService;

  @Test
  void registrar_conSumaInvalida_lanzaYNoPersiste() {
    var prov = nuevaProvincia();
    var canton = nuevoCanton(prov);
    var parro = nuevaParroquia(canton);
    var jrv = nuevoJrv(parro);
    var cargo = nuevoCargo("Prefecto", ec.twintailcs.electoral.electoral.domain.NivelCargo.PROVINCIA);
    habilitarCargoEnJrv(jrv, cargo);
    var partido = nuevoPartido("X");
    var c1 = nuevoCandidato(cargo, partido, "C1");
    var observer = nuevoObserver();

    // Suma NO cuadra: candidato=50, blancos=5, nulos=15 → total 70. Declaramos 100.
    ActaRequestDto dto =
        new ActaRequestDto(
            jrv.id(),
            cargo.id(),
            100, // totalSufragantes declarado
            5,
            15,
            List.of(new ActaDetalleDto(c1.getId(), 50)));

    AtomicLong actaCountBefore = new AtomicLong(countActa());
    assertThatThrownBy(() -> actaService.registrar(dto, observer.id(), "127.0.0.1"))
        .isInstanceOf(ActaInvalidaException.class)
        .hasMessageContaining("no cuadran");

    assertThat(countActa())
        .as("registrar con suma inválida NO debe crear fila en acta")
        .isEqualTo(actaCountBefore.get());
    assertThat(countActaDetalle())
        .as("registrar con suma inválida NO debe crear filas en acta_detalle")
        .isZero();
  }

  @Test
  void registrar_conSumaValida_creaActaYDetalle() {
    var prov = nuevaProvincia();
    var canton = nuevoCanton(prov);
    var parro = nuevaParroquia(canton);
    var jrv = nuevoJrv(parro);
    var cargo = nuevoCargo("Prefecto", ec.twintailcs.electoral.electoral.domain.NivelCargo.PROVINCIA);
    habilitarCargoEnJrv(jrv, cargo);
    var partido = nuevoPartido("X");
    var c1 = nuevoCandidato(cargo, partido, "C1");
    var observer = nuevoObserver();

    // Suma cuadra: 80 + 5 + 15 = 100
    ActaRequestDto dto = actaRequestValida(jrv, cargo, 80, 5, 15, List.of(c1));

    Acta acta = actaService.registrar(dto, observer.id(), "127.0.0.1");
    assertThat(acta.getId()).isNotNull();
    assertThat(acta.getEstado().name()).isEqualTo("ENVIADA");
  }

  @Test
  void corregir_conSumaInvalida_lanzaYNoPersisteCambios() {
    // Setup: acta EN_DISPUTA registrada
    var prov = nuevaProvincia();
    var canton = nuevoCanton(prov);
    var parro = nuevaParroquia(canton);
    var jrv = nuevoJrv(parro);
    var cargo = nuevoCargo("Prefecto", ec.twintailcs.electoral.electoral.domain.NivelCargo.PROVINCIA);
    habilitarCargoEnJrv(jrv, cargo);
    var partido = nuevoPartido("X");
    var c1 = nuevoCandidato(cargo, partido, "C1");
    var observer = nuevoObserver();

    ActaRequestDto valida = actaRequestValida(jrv, cargo, 80, 5, 15, List.of(c1));
    Acta acta = actaService.registrar(valida, observer.id(), "127.0.0.1");

    // Llevar a EN_DISPUTA vía coordinator.
    actaService.marcarEnDisputa(acta.getId(), nuevoCoordinator().id(), "Motivo X", "127.0.0.1");

    // Intentar corregir con suma inválida.
    ActaRequestDto invalida =
        new ActaRequestDto(
            jrv.id(), cargo.id(), 999, // suma declarada NO cuadra
            0, 0, List.of(new ActaDetalleDto(c1.getId(), 0)));

    assertThatThrownBy(
            () ->
                actaService.corregir(
                    acta.getId(), invalida, observer.id(), false, "127.0.0.1"))
        .isInstanceOf(ActaInvalidaException.class)
        .hasMessageContaining("no cuadran");

    // El acta debe seguir en EN_DISPUTA (corregir falló, no se persistió).
    Acta refreshed = actaPorId(acta.getId());
    assertThat(refreshed.getEstado().name()).isEqualTo("EN_DISPUTA");
    assertThat(refreshed.getTotalSufragantes()).isEqualTo(100);
  }

  private long countActa() {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM acta", Long.class);
    return n == null ? 0L : n;
  }

  private long countActaDetalle() {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM acta_detalle", Long.class);
    return n == null ? 0L : n;
  }
}
