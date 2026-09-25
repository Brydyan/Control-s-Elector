package ec.twintailcs.electoral.acta;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.domain.JrvCargoYaRegistradoException;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import ec.twintailcs.electoral.electoral.domain.Partido;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RF-TEST-01 — Unicidad de acta por {@code jrv_id + cargo_id} bajo concurrencia real.
 *
 * <p>Diseño (per design.md D2):
 *
 * <ul>
 *   <li>Dos hilos arrancan a la vez con {@link CountDownLatch} para garantizar simultaneidad.
 *   <li>Si el test se hiciera con dos llamadas SECUENCIALES, pasaría incluso sin locking:
 *       la segunda llamada encontraría el acta de la primera con un SELECT normal. Aquí
 *       forzamos la carrera.
 *   <li>Timeout explícito de 10s — los tests de concurrencia son intermitentes por
 *       naturaleza; un timeout las acota.
 *   <li>Aserciones:
 *       <ul>
 *         <li>Exactamente UNO gana.
 *         <li>El otro recibe {@link JrvCargoYaRegistradoException}.
 *         <li>La BD tiene UNA sola fila activa para ese par.
 *       </ul>
 *   <li>Segundo escenario: con acta ANULADA previa, una nueva debe permitirse
 *       (per spec RF-TEST-01 escenario 2).
 * </ul>
 *
 * <p>Mutación C.1: reemplazar {@code findActaActivaConLock} por {@code findActaActiva}
 * en RegistrarActaUseCase. Debe hacer caer este test.
 */
class ActaConcurrencyIT extends ActaIntegrationHarness {
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
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  void dosHilosMismoJrvCargoSoloUnoGana() throws Exception {
    var prov = nuevaProvincia();
    var canton = nuevoCanton(prov);
    var parro = nuevaParroquia(canton);
    var jrv = nuevoJrv(parro);
    var cargo = nuevoCargo("Prefecto", ec.twintailcs.electoral.electoral.domain.NivelCargo.PROVINCIA);
    habilitarCargoEnJrv(jrv, cargo);
    var partido = nuevoPartido("ALIANZA");
    var c1 = nuevoCandidato(cargo, partido, "Candidato 1");
    var observerA = nuevoObserver();
    var observerB = nuevoObserver();

    ActaRequestDto dtoA = actaRequestValida(jrv, cargo, 80, 5, 15, List.of(c1));
    ActaRequestDto dtoB = actaRequestValida(jrv, cargo, 80, 5, 15, List.of(c1));

    // Forzar el binding RLS en el hilo principal (los workers usan su propio observerId manualmente).
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);
    AtomicInteger successCount = new AtomicInteger();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    ExecutorService exec = Executors.newFixedThreadPool(2);
    try {
      exec.submit(
          () -> {
            try {
              start.await();
              actaService.registrar(dtoA, observerA.id(), "127.0.0.1");
              successCount.incrementAndGet();
            } catch (JrvCargoYaRegistradoException e) {
              failure.set(e);
            } catch (Throwable t) {
              failure.set(t);
            } finally {
              done.countDown();
            }
          });
      exec.submit(
          () -> {
            try {
              start.await();
              actaService.registrar(dtoB, observerB.id(), "127.0.0.1");
              successCount.incrementAndGet();
            } catch (JrvCargoYaRegistradoException e) {
              failure.set(e);
            } catch (Throwable t) {
              failure.set(t);
            } finally {
              done.countDown();
            }
          });

      start.countDown(); // liberar ambos a la vez
      assertThat(done.await(10, TimeUnit.SECONDS))
          .as("ambos hilos deben terminar dentro del timeout")
          .isTrue();
    } finally {
      exec.shutdownNow();
    }

    assertThat(successCount.get())
        .as("exactamente UNO debe ganar la carrera; el otro recibe JrvCargoYaRegistradoException. "
            + "Si ambos ganan, el lock pesimista no está protegiendo. "
            + "Si ninguno gana, el error no es JrvCargoYaRegistradoException: %s",
            failure.get())
        .isEqualTo(1);
    assertThat(failure.get())
        .as("el hilo perdedor debe recibir JrvCargoYaRegistradoException")
        .isInstanceOf(JrvCargoYaRegistradoException.class);

    // Y la BD debe tener exactamente UNA fila activa para ese par.
    Long activas =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM acta WHERE jrv_id = ? AND cargo_id = ? AND estado != 'ANULADA'",
            Long.class,
            jrv.id(),
            cargo.id());
    assertThat(activas)
        .as("solo UN acta activa debe existir para el par (jrv, cargo)")
        .isEqualTo(1L);
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  void actaAnuladaPermiteNuevoRegistro() {
    var prov = nuevaProvincia();
    var canton = nuevoCanton(prov);
    var parro = nuevaParroquia(canton);
    var jrv = nuevoJrv(parro);
    var cargo = nuevoCargo("Alcalde", ec.twintailcs.electoral.electoral.domain.NivelCargo.CANTON);
    habilitarCargoEnJrv(jrv, cargo);
    var partido = nuevoPartido("ALIANZA");
    var c1 = nuevoCandidato(cargo, partido, "Candidato 1");
    var observerA = nuevoObserver();

    // Pre-crear acta ANULADA para el mismo par.
    jdbc.update(
        "INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado, total_sufragantes, "
            + "votos_blancos, votos_nulos, public_id, version) "
            + "VALUES (?,?,?,?,?,?,?,?,0)",
        jrv.id(),
        cargo.id(),
        observerA.id(),
        "ANULADA",
        0,
        0,
        0,
        java.util.UUID.randomUUID());

    ActaRequestDto dto = actaRequestValida(jrv, cargo, 80, 5, 15, List.of(c1));
    Acta acta = actaService.registrar(dto, observerA.id(), "127.0.0.1");
    assertThat(acta.getEstado()).isEqualTo(EstadoActa.ENVIADA);
  }
}
