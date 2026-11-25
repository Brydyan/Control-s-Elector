package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.infrastructure.email.RecordingEmailService;
import ec.twintailcs.electoral.shared.testing.PostgresTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.QuarkusTestProfile.TestResourceEntry;
import java.util.Collections;
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
 *   <li><b>El proveedor de email</b>: si APP_EMAIL_PROVIDER=resend, usa ResendEmailProvider
 *       (integración real con Resend API). Si no, usa RecordingEmailService (mock local).
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
    // Try system property first (from Maven -D flag), then env var
    String provider = System.getProperty("app.email.provider", System.getenv("APP_EMAIL_PROVIDER"));
    // Si APP_EMAIL_PROVIDER=resend, NO usar RecordingEmailService.
    // En su lugar, ResendEmailProvider realmente llamará a Resend API.
    // Para tests locales, dejar APP_EMAIL_PROVIDER sin setear (default=smtp + Recording).
    if ("resend".equals(provider)) {
      return Collections.emptySet(); // Sin alternatives, el productor elige según app.email.provider
    }
    return Set.of(RecordingEmailService.class);
  }

  @Override
  public Map<String, String> getConfigOverrides() {
    // Try system property first (from Maven -D flag), then env var
    // Must match getEnabledAlternatives() logic for consistency
    String provider = System.getProperty("app.email.provider", System.getenv("APP_EMAIL_PROVIDER"));
    Map<String, String> overrides = new java.util.HashMap<>(Map.of(
        "quarkus.liquibase.migrate-at-start", "true",
        "quarkus.liquibase.contexts", "seed",
        "quarkus.hibernate-orm.database.generation", "validate",
        "app.email.queue.poll-interval-ms", "200"));

    // Si APP_EMAIL_PROVIDER está seteada (via -D o env var), usa esa (smtp o resend)
    if (provider != null && !provider.isBlank()) {
      overrides.put("app.email.provider", provider);
    }
    // Si no, default es smtp (con RecordingEmailService como Alternative arriba)

    return overrides;
  }
}
