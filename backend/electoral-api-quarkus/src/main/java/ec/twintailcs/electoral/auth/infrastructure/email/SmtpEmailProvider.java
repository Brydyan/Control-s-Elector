package ec.twintailcs.electoral.auth.infrastructure.email;

import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envio de email por SMTP, con el mailer de Quarkus.
 *
 * <p>El bean solo se activa cuando {@code app.email.provider=smtp} (decision D7 del design: el
 * proveedor es intercambiable). Si se configura otro valor, este bean no existe y la inyeccion de
 * {@link EmailService} falla al arrancar — que es lo que tiene que pasar: es preferible que el
 * despliegue no levante con un proveedor inexistente a que los correos salgan a un destino
 * equivocado en silencio.
 *
 * <p>El metodo no captura la excepcion: el reintento con backoff y la auditoria de cada intento
 * viven por encima de esta interfaz, y para poder reintentar el fallo tiene que propagarse.
 */
@ApplicationScoped
@LookupIfProperty(name = "app.email.provider", stringValue = "smtp")
public class SmtpEmailProvider implements EmailService {

  private final Mailer mailer;
  private final String fromEmail;

  public SmtpEmailProvider(
      Mailer mailer,
      @org.eclipse.microprofile.config.inject.ConfigProperty(name = "app.email.from")
          String fromEmail) {
    this.mailer = mailer;
    this.fromEmail = fromEmail;
  }

  @Override
  public void sendInvitationEmail(EmailAEnviar email) {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("nombre", email.nombre());
    variables.put("apellido", email.apellido());
    variables.put("nombreCompleto", email.nombreCompleto());
    variables.put("rol", email.rol());
    variables.put("activationLink", email.activationLink());
    variables.put("tokenExpira", email.tokenExpira());

    // El reenvio usa su propia plantilla: el link anterior quedo invalidado y el
    // usuario tiene que saberlo, no cree que es el mismo email de antes.
    EmailTemplates plantilla =
        email.tipo() == EmailAEnviar.TipoEmail.REENVIO
            ? EmailTemplates.RESEND
            : EmailTemplates.INVITATION;

    String html = plantilla.render(variables);

    Mail mail = new Mail();
    mail.setFrom(fromEmail);
    mail.addTo(email.destinatario());
    mail.setSubject("Activa tu cuenta en Control Electoral");
    mail.setHtml(html);
    mailer.send(mail);
  }

  @Override
  public void sendActivationConfirmation(String destinatario, String nombreCompleto) {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("nombreCompleto", nombreCompleto);

    String html = EmailTemplates.CONFIRMATION.render(variables);

    Mail mail = new Mail();
    mail.setFrom(fromEmail);
    mail.addTo(destinatario);
    mail.setSubject("Tu cuenta de Control Electoral ya esta activa");
    mail.setHtml(html);
    mailer.send(mail);
  }
}
