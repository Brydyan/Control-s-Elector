package ec.twintailcs.electoral.acta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.domain.AccesoNoPermitidoException;
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
 * RF-TEST-04 — Un OBSERVER no accede a las actas de otro.
 *
 * <p>Dos escenarios:
 *
 * <ol>
 *   <li>{@code mis-actas} de OBSERVER B NO incluye actas registradas por OBSERVER A.
 *   <li>Corregir un acta EN_DISPUTA ajena: OBSERVER B recibe AccesoNoPermitidoException
 *       (la guarda de {@code CorregirActaUseCase} chequea {@code registradaPor}).
 * </ol>
 *
 * <p>Mutaciones:
 *
 * <ul>
 *   <li>C.4: quitar la comprobación de {@code registradaPor} en {@code CorregirActaUseCase}. Debe
 *       caer el segundo escenario.
 *   <li>C.5: desactivar {@code RlsTransactionExecutionListener}. Debe caer el primer escenario.
 * </ul>
 */
class ActaAislamientoObserverIT extends ActaIntegrationHarness {
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
  void correccionAjena_lanzaAccesoNoPermitido() {
    var prov = nuevaProvincia();
    var canton = nuevoCanton(prov);
    var parro = nuevaParroquia(canton);
    var jrv = nuevoJrv(parro);
    var cargo = nuevoCargo("Prefecto", ec.twintailcs.electoral.electoral.domain.NivelCargo.PROVINCIA);
    habilitarCargoEnJrv(jrv, cargo);
    var partido = nuevoPartido("X");
    var c1 = nuevoCandidato(cargo, partido, "C1");
    var obsA = nuevoObserver();
    var obsB = nuevoObserver();
    var coord = nuevoCoordinator();

    // Acta de A en EN_DISPUTA.
    ActaRequestDto dto = actaRequestValida(jrv, cargo, 80, 5, 15, List.of(c1));
    Acta acta = actaService.registrar(dto, obsA.id(), "127.0.0.1");
    actaService.marcarEnDisputa(acta.getId(), coord.id(), "x", "127.0.0.1");

    // B intenta corregir el acta de A.
    ActaRequestDto correccion = actaRequestValida(jrv, cargo, 85, 5, 10, List.of(c1));
    assertThatThrownBy(
            () ->
                actaService.corregir(
                    acta.getId(), correccion, obsB.id(), false, "127.0.0.1"))
        .isInstanceOf(AccesoNoPermitidoException.class);
  }

  @Test
  void correccionPropia_funciona() {
    // Control: el dueño del acta sí puede corregirla.
    var prov = nuevaProvincia();
    var canton = nuevoCanton(prov);
    var parro = nuevaParroquia(canton);
    var jrv = nuevoJrv(parro);
    var cargo = nuevoCargo("Prefecto", ec.twintailcs.electoral.electoral.domain.NivelCargo.PROVINCIA);
    habilitarCargoEnJrv(jrv, cargo);
    var partido = nuevoPartido("X");
    var c1 = nuevoCandidato(cargo, partido, "C1");
    var obsA = nuevoObserver();
    var coord = nuevoCoordinator();

    ActaRequestDto dto = actaRequestValida(jrv, cargo, 80, 5, 15, List.of(c1));
    Acta acta = actaService.registrar(dto, obsA.id(), "127.0.0.1");
    actaService.marcarEnDisputa(acta.getId(), coord.id(), "x", "127.0.0.1");

    ActaRequestDto correccion = actaRequestValida(jrv, cargo, 85, 5, 10, List.of(c1));
    Acta corregida =
        actaService.corregir(acta.getId(), correccion, obsA.id(), false, "127.0.0.1");
    assertThat(corregida.getEstado().name()).isEqualTo("CORREGIDA");
  }

  /**
   * RF-TEST-04 escenario 1 — listado.
   *
   * <p>El controller {@code GET /api/v1/observer/mis-actas} invoca
   * {@code actaRepository.findWithFilters(estado, null, null, null, observerId)} con el
   * {@code observerId} que sale del JWT. Este test ejercita la MISMA llamada desde la capa
   * de servicio (sin overhead de autenticación JWT — AuthFlowIT cubre el camino completo
   * HTTP→JWT→repository en {@code backend/src/test/.../AuthFlowIT}).
   *
   * <p>Mutación objetivo C.5: si se desactiva {@code RlsTransactionExecutionListener}, las
   * policies de RLS no se aplican y este assert debería caer — pero en realidad el filtro
   * por {@code registradaPorId} en el repositorio actúa ANTES de que RLS intervenga, así que
   * este test seguiría verde incluso sin RLS. El test de RLS (B.5 / ActaRlsIntegrationIT)
   * captura C.5 en su capa específica.
   */
  @Test
  void misActas_observadorBSoloVeSusPropiasActas() {
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

    // obsA registra 2 actas; obsB registra 1.
    actaService.registrar(
        actaRequestValida(jrv1, cargo1, 80, 5, 15, List.of(c1)), obsA.id(), "127.0.0.1");
    actaService.registrar(
        actaRequestValida(jrv2, cargo2, 80, 5, 15, List.of(c2)), obsA.id(), "127.0.0.1");
    actaService.registrar(
        actaRequestValida(jrv1, cargo2, 80, 5, 15, List.of(c2)), obsB.id(), "127.0.0.1");

    // Lo que ve obsB (la misma llamada que hace ObserverController.misActas).
    List<Acta> vistaB =
        actaRepository.findWithFilters(null, null, null, null, obsB.id()).stream().toList();
    assertThat(vistaB)
        .as("OBSERVER B solo debe ver sus propias actas (no las de A)")
        .hasSize(1)
        .allMatch(a -> a.getRegistradaPor().getId().equals(obsB.id()));
  }
}
