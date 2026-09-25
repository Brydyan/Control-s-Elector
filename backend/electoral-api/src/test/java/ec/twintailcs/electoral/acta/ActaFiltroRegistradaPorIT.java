package ec.twintailcs.electoral.acta;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import ec.twintailcs.electoral.electoral.domain.Partido;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RF-TEST-06 — Filtro por OBSERVER del listado global (COORDINATOR).
 *
 * <p>Actas de dos OBSERVERs distintos; COORDINATOR consulta
 * {@code findWithFilters(..., registradaPorId=idDeA, ...)}. Solo aparecen las de A.
 *
 * <p>Mutación C.6: quitar la cláusula {@code (:registradaPorId IS NULL OR ...)} de {@code
 * ActaRepository.findWithFilters}. Debe hacer caer este test (el filtro se ignora, devuelve
 * todas las actas).
 */
class ActaFiltroRegistradaPorIT extends ActaIntegrationHarness {
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
  void filtroPorRegistradaPorIdDevuelveSoloActasDelObservador() {
    var prov = nuevaProvincia();
    var canton = nuevoCanton(prov);
    var parro = nuevaParroquia(canton);
    var jrv1 = nuevoJrv(parro);
    var jrv2 = nuevoJrv(parro);
    var cargo1 = nuevoCargo("Prefecto", ec.twintailcs.electoral.electoral.domain.NivelCargo.PROVINCIA);
    var cargo2 = nuevoCargo("Alcalde", ec.twintailcs.electoral.electoral.domain.NivelCargo.CANTON);
    habilitarCargoEnJrv(jrv1, cargo1);
    habilitarCargoEnJrv(jrv1, cargo2);
    habilitarCargoEnJrv(jrv2, cargo2);
    var partido = nuevoPartido("X");
    var c1 = nuevoCandidato(cargo1, partido, "C1");
    var c2 = nuevoCandidato(cargo2, partido, "C2");
    var obsA = nuevoObserver();
    var obsB = nuevoObserver();

    // obsA registra 2 actas (en 2 JRV distintos para evitar colisión jrv+cargo).
    actaService.registrar(
        actaRequestValida(jrv1, cargo1, 80, 5, 15, List.of(c1)), obsA.id(), "127.0.0.1");
    actaService.registrar(
        actaRequestValida(jrv2, cargo2, 80, 5, 15, List.of(c2)), obsA.id(), "127.0.0.1");
    // obsB registra 1 acta (en otra JRV).
    actaService.registrar(
        actaRequestValida(jrv1, cargo2, 80, 5, 15, List.of(c2)), obsB.id(), "127.0.0.1");

    // Filtrar por obsA.
    List<Acta> actasA =
        actaRepository.findWithFilters(null, null, null, null, obsA.id()).stream().toList();
    assertThat(actasA)
        .as("solo las 2 actas de obsA deben aparecer")
        .hasSize(2)
        .allMatch(a -> a.getRegistradaPor().getId().equals(obsA.id()));

    // Filtrar por obsB.
    List<Acta> actasB =
        actaRepository.findWithFilters(null, null, null, null, obsB.id()).stream().toList();
    assertThat(actasB)
        .as("solo la 1 acta de obsB debe aparecer")
        .hasSize(1)
        .allMatch(a -> a.getRegistradaPor().getId().equals(obsB.id()));

    // Sin filtro: todas.
    List<Acta> todas =
        actaRepository.findWithFilters(null, null, null, null, null).stream().toList();
    assertThat(todas).as("sin filtro, todas las actas visibles").hasSize(3);
  }
}
