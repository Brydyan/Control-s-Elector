package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.infrastructure.email.RecordingEmailService;
import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.QuarkusTestProfile.TestResourceEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Perfil de los tests del flujo de invitacion (T-AUTH-INV-02).
 *
 * <p>Tres cosas, cada una por un motivo:
 *
 * <ul>
 *   <li><b>Postgres real</b> (Testcontainers): la invitacion escribe en {@code usuario} y en {@code
 *       user_invitations}, y la segunda tiene RLS forzada. Un test con base de mentira no probaria
 *       ni el INSERT ni el contexto de RLS.
 *   <li><b>El proveedor de email de grabacion</b>: el test afirma que se mando un email, a quien y
 *       con que contenido, sin depender de un SMTP.
 *   <li><b>El worker de la cola con un poll corto</b> (200 ms): el envio es asincrono, asi que el
 *       test espera a que el worker lo procese. Con el poll por defecto (5 s) el test tendria que
 *       esperar a que el reloj pegara.
 * </ul>
 */
public class UserInvitationTestProfile implements QuarkusTestProfile {

  @Override
  public List<TestResourceEntry> testResources() {
    return List.of(new TestResourceEntry(PostgresTestResource.class));
  }

  @Override
  public Set<Class<?>> getEnabledAlternatives() {
    return Set.of(RecordingEmailService.class);
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.liquibase.migrate-at-start", "true",
        "quarkus.liquibase.contexts", "seed",
        "quarkus.hibernate-orm.database.generation", "validate",
        "app.email.queue.poll-interval-ms", "200");
  }
}
