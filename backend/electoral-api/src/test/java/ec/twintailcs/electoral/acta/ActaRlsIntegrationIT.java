package ec.twintailcs.electoral.acta;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.shared.security.RlsContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RF-TEST-05 — RLS está realmente activa durante una petición.
 *
 * <p>Diseño (per design.md D3):
 *
 * <ul>
 *   <li>Abrimos una transacción explícita con {@link TransactionTemplate} para garantizar que
 *       podemos registrar un {@code TransactionSynchronization} DENTRO de ella (registrar
 *       fuera tira {@code IllegalStateException}).
 *   <li>Configuramos {@code RlsContext} como si {@code RlsAuthenticationBridge} hubiera corrido.
 *   <li>En {@code beforeCommit} consultamos {@code current_setting('app.current_user_id',
 *       true)} — si RLS está activa, {@code RlsTransactionExecutionListener} ya seteo la
 *       variable en {@code afterBegin}.
 * </ul>
 *
 * <p>Si la implementación actual de RLS no setea la variable dentro de la tx, este test va a
 * fallar — el comentario de ActaService:77-82 sería una defensa inexistente (per spec §4.3 +
 * tasks.md B.5).
 */
class ActaRlsIntegrationIT extends ActaIntegrationHarness {
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
  @Autowired private PlatformTransactionManager txManager;

  @Test
  void rlsContextDentroDeTransaccionDevuelveIdInterno() {
    var prov = nuevaProvincia();
    var canton = nuevoCanton(prov);
    var parro = nuevaParroquia(canton);
    var jrv = nuevoJrv(parro);
    var cargo = nuevoCargo("Prefecto", ec.twintailcs.electoral.electoral.domain.NivelCargo.PROVINCIA);
    habilitarCargoEnJrv(jrv, cargo);
    var partido = nuevoPartido("X");
    var c1 = nuevoCandidato(cargo, partido, "C1");
    var obs = nuevoObserver();

    // Configurar RLS como si RlsAuthenticationBridge hubiera corrido.
    RlsContext.set(obs.id(), "OBSERVER");

    AtomicReference<String> rlsUserIdInTx = new AtomicReference<>();

    try {
      new TransactionTemplate(txManager)
          .execute(
              status -> {
                // Registrar sync que lee current_setting DENTRO de la tx abierta.
                org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                          @Override
                          public void beforeCommit(boolean readOnly) {
                            rlsUserIdInTx.set(currentRlsUserId());
                          }
                        });
                // Llamar al servicio (que también es @Transactional — se une a esta tx).
                ActaRequestDto dto =
                    actaRequestValida(jrv, cargo, 80, 5, 15, List.of(c1));
                actaService.registrar(dto, obs.id(), "127.0.0.1");
                return null;
              });
    } finally {
      RlsContext.clear();
    }

    String observed = rlsUserIdInTx.get();
    assertThat(observed)
        .as(
            "RF-TEST-05: dentro de la tx del servicio, current_setting('app.current_user_id', "
                + "true) debe devolver el id del OBSERVER autenticado. Si devuelve '' o null, RLS "
                + "no está actuando y el comentario de ActaService es una defensa inexistente. "
                + "Actual: '%s'",
            observed)
        .isEqualTo(String.valueOf(obs.id()));
  }
}

