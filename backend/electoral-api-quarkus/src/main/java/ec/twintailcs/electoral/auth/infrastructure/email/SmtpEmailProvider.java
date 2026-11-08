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
