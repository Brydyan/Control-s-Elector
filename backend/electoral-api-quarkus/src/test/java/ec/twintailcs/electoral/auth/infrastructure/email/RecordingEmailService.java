package ec.twintailcs.electoral.auth.infrastructure.email;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Proveedor de email que guarda lo que se habria enviado, en vez de mandarlo.
 *
 * <p>Es una alternativa de test: el perfil {@code UserInvitationTestProfile} la activa, y mientras
 * este bean exista el {@code SmtpEmailProvider} real no se usa. Asi el test puede afirmar QUE se
 * mando (a quien y con que contenido) sin depender de un servidor SMTP ni del hilo del worker.
 *
 * <p>La lista es estatica a proposito: el bean vive en el contenedor de la aplicacion y el test
 * corre en otro contexto, pero en el mismo JVM.
 */
@Alternative
@ApplicationScoped
public class RecordingEmailService implements EmailService {

  /** Los emails "enviados", en orden. */
  private static final List<EmailAEnviar> ENVIADOS = new CopyOnWriteArrayList<>();

  /** Limpia lo grabado. Se llama antes de cada test. */
  public static void limpiar() {
    ENVIADOS.clear();
  }

  /** Lo grabado hasta ahora. */
  public static List<EmailAEnviar> enviados() {
    return List.copyOf(ENVIADOS);
  }

  @Override
  public void sendInvitationEmail(EmailAEnviar email) {
    ENVIADOS.add(email);
  }

  @Override
  public void sendActivationConfirmation(String destinatario, String nombreCompleto) {
    // La confirmacion no es lo que estos tests verifican: no se graba.
  }

  @PreDestroy
  void limpiarAlApagar() {
    ENVIADOS.clear();
  }
}
