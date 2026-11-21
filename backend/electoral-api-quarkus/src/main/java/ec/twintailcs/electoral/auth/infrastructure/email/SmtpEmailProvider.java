package ec.twintailcs.electoral.auth.infrastructure.email;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.Dependent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envio de email por SMTP, con el mailer de Quarkus.
 *
 * <p>Este bean es inyectado por EmailServiceProducer. Marcado con @SmtpProvider para que CDI pueda
 * distinguirlo de otras implementaciones de EmailService. El productor selecciona la implementación
 * correcta basado en {@code app.email.provider}.
 *
 * <p>El metodo no captura la excepcion: el reintento con backoff y la auditoria de cada intento
 * viven por encima de esta interfaz, y para poder reintentar el fallo tiene que propagarse.
 */
@ec.twintailcs.electoral.auth.infrastructure.email.SmtpProvider
@Dependent
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
    variables.put("username", email.username());
    // El usuario de Control sin dominio ("juagarobs") y el correo de Control
    // ("juagarobs@control.ttcs") son dos datos distintos en la pantalla: el
    // primero identifica, el segundo es con el que se entra (ODD #504, A.5.2).
    variables.put("usuarioCorto", usuarioCorto(email.username()));
    // El destinatario es el correo de contacto de la persona: el email dice de
    // dos colores de que direccion se trata, porque no son la misma (ODD #504,
    // A.5.2).
    variables.put("emailPersonal", email.destinatario());
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
    mail.setSubject(asunto(email.tipo()));
    mail.setHtml(html);
    mailer.send(mail);
  }

  /**
   * Asunto del correo.
   *
   * <p>La invitacion inicial y el reenvio no dicen lo mismo: en el reenvio la persona ya conoce la
   * plataforma y lo que necesita saber es que el enlace viejo dejo de servir.
   */
  private String asunto(EmailAEnviar.TipoEmail tipo) {
    return tipo == EmailAEnviar.TipoEmail.REENVIO
        ? "Reenviamos tu invitación a Control's Elector"
        : "Bienvenido a Control's Elector - Activa tu cuenta";
  }

  /**
   * El login sin el dominio: "juagarobs@control.ttcs" -&gt; "juagarobs".
   *
   * <p>Es el usuario de Control que la persona reconoce; el correo completo es con el que entra al
   * sistema (ODD #504, A.5.2).
   */
  private static String usuarioCorto(String username) {
    if (username == null || username.isBlank()) {
      return "";
    }
    int arroba = username.indexOf('@');
    return arroba > 0 ? username.substring(0, arroba) : username;
  }

  @Override
  public void sendActivationConfirmation(String destinatario, String nombreCompleto) {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("nombreCompleto", nombreCompleto);

    String html = EmailTemplates.CONFIRMATION.render(variables);

    Mail mail = new Mail();
    mail.setFrom(fromEmail);
    mail.addTo(destinatario);
    mail.setSubject("Tu cuenta de Control's Elector ya esta activa");
    mail.setHtml(html);
    mailer.send(mail);
  }
}
