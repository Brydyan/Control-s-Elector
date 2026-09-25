package ec.twintailcs.electoral.acta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
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
 * RF-TEST-03 — Ciclo de vida completo del acta en UN SOLO test.
 *
 * <p>Recorre ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA → ANULADA verificando:
 *
 * <ul>
 *   <li>Cada transición queda registrada en {@code audit_log}.
 *   <li>EN_DISPUTA y ANULADA generan {@code notificacion} para el OBSERVER registrado.
 *   <li>Desde ANULADA no se admite ninguna transición (estado terminal).
 * </ul>
 *
 * <p>Por qué UN SOLO test (per spec RF-TEST-03): probar cada transición por separado, con el
 * estado fijado a mano, deja sin verificar que la salida de un paso es entrada válida del
 * siguiente. El camino completo expone ese tipo de bug.
 *
 * <p>Mutación C.3: ampliar el guard de {@code aprobar} para aceptar EN_DISPUTA (saltarse la
 * transición CORREGIDA). Debe hacer caer este test porque tras EN_DISPUTA el flujo espera
 * CORREGIDA antes de APROBADA.
 */
class ActaCicloVidaIT extends ActaIntegrationHarness {
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
  void cicloCompletoEnUnSoloTest() {
    var prov = nuevaProvincia();
    var canton = nuevoCanton(prov);
    var parro = nuevaParroquia(canton);
    var jrv = nuevoJrv(parro);
    var cargo = nuevoCargo("Prefecto", ec.twintailcs.electoral.electoral.domain.NivelCargo.PROVINCIA);
    habilitarCargoEnJrv(jrv, cargo);
    var partido = nuevoPartido("PARTIDO A");
    var c1 = nuevoCandidato(cargo, partido, "Candidato 1");
    var observer = nuevoObserver();
    var coord = nuevoCoordinator();
    var master = nuevoMaster();

    // Limpiar contadores para que el conteo sea relativo a este test.
    long auditBefore = contarAuditLog();
    long notifBefore = contarNotificaciones();

    // 1) ENVIADA
    ActaRequestDto valida = actaRequestValida(jrv, cargo, 80, 5, 15, List.of(c1));
    Acta acta = actaService.registrar(valida, observer.id(), "127.0.0.1");
    assertThat(acta.getEstado().name()).isEqualTo("ENVIADA");

    // 2) EN_DISPUTA (con motivo) — genera notificación al OBSERVER
    actaService.marcarEnDisputa(acta.getId(), coord.id(), "duda en conteo", "127.0.0.1");
    assertThat(actaPorId(acta.getId()).getEstado().name()).isEqualTo("EN_DISPUTA");

    // Sub-test del guard: aprobar un acta EN_DISPUTA directamente debe rechazarse.
    // (Mutación C.3 lo amplía; este assert es el que la captura.)
    assertThatThrownBy(() -> actaService.aprobar(acta.getId(), coord.id(), "127.0.0.1"))
        .as("aprobar debe rechazar EN_DISPUTA (forzar paso por CORREGIDA)")
        .isInstanceOf(ActaEstadoInvalidoException.class);

    // 3) CORREGIDA
    ActaRequestDto corregida = actaRequestValida(jrv, cargo, 85, 5, 10, List.of(c1));
    actaService.corregir(acta.getId(), corregida, observer.id(), false, "127.0.0.1");
    assertThat(actaPorId(acta.getId()).getEstado().name()).isEqualTo("CORREGIDA");

    // 4) APROBADA
    actaService.aprobar(acta.getId(), coord.id(), "127.0.0.1");
    assertThat(actaPorId(acta.getId()).getEstado().name()).isEqualTo("APROBADA");

    // 5) ANULADA (con motivo) — genera notificación
    actaService.anular(acta.getId(), master.id(), "fraude detectado", "127.0.0.1");
    assertThat(actaPorId(acta.getId()).getEstado().name()).isEqualTo("ANULADA");

    // Desde ANULADA no se admite ninguna transición.
    final Long idAnulada = acta.getId();
    assertThatThrownBy(
            () -> actaService.aprobar(idAnulada, coord.id(), "127.0.0.1"))
        .isInstanceOf(ActaEstadoInvalidoException.class);
    assertThatThrownBy(
            () -> actaService.marcarEnDisputa(idAnulada, coord.id(), "x", "127.0.0.1"))
        .isInstanceOf(ActaEstadoInvalidoException.class);

    // Conteo de audit_log: 5 transiciones (registrar, enDisputa, corregir, aprobar, anular).
    long auditDelta = contarAuditLog() - auditBefore;
    assertThat(auditDelta)
        .as("5 transiciones deben generar 5 filas en audit_log; delta=%d", auditDelta)
        .isEqualTo(5L);

    // Conteo de notificacion: enDisputa + anular = 2.
    long notifDelta = contarNotificaciones() - notifBefore;
    assertThat(notifDelta)
        .as("enDisputa + anular deben generar 2 notificaciones; delta=%d", notifDelta)
        .isEqualTo(2L);
  }
}
