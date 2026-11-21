package ec.twintailcs.electoral.auth.infrastructure.email;

import jakarta.enterprise.context.Dependent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Envio de email por Resend.com.
 *
 * <p>Este bean es inyectado por EmailServiceProducer. Marcado con @ResendProvider para que CDI
 * pueda distinguirlo de otras implementaciones de EmailService. El productor selecciona la
 * implementación correcta basado en {@code app.email.provider=resend}. Requiere variable de entorno
 * {@code RESEND_API_KEY} configurada con la API key de Resend.
 *
 * <p>El metodo no captura la excepcion: el reintento con backoff y la auditoria de cada intento
 * viven por encima de esta interfaz, y para poder reintentar el fallo tiene que propagarse.
 *
 * <p>DNS CNAME debe estar propagado antes de enviar: <code>
 * Type: CNAME
 * Host: send.mail
 * Value: send.forge.rmta.net
 * </code>
 */
@ec.twintailcs.electoral.auth.infrastructure.email.ResendProvider
@Dependent
public class ResendEmailProvider implements EmailService {

  private static final Logger log = Logger.getLogger(ResendEmailProvider.class);
  private static final String RESEND_API_URL = "https://api.resend.com/emails";

  private final String resendApiKey;
  private final String fromEmail;
  private final HttpClient httpClient;

  public ResendEmailProvider(
      @org.eclipse.microprofile.config.inject.ConfigProperty(name = "app.email.from")
          String fromEmail,
      @org.eclipse.microprofile.config.inject.ConfigProperty(
              name = "RESEND_API_KEY",
              defaultValue = "")
          String resendApiKey) {
    this.fromEmail = fromEmail;
    this.resendApiKey = resendApiKey;
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public void sendInvitationEmail(EmailAEnviar email) {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("nombre", email.nombre());
    variables.put("apellido", email.apellido());
    variables.put("nombreCompleto", email.nombreCompleto());
    variables.put("rol", email.rol());
    variables.put("username", email.username());
    variables.put("usuarioCorto", usuarioCorto(email.username()));
    variables.put("emailPersonal", email.destinatario());
    variables.put("activationLink", email.activationLink());
    variables.put("tokenExpira", email.tokenExpira());

    // El reenvio usa su propia plantilla
    EmailTemplates plantilla =
        email.tipo() == EmailAEnviar.TipoEmail.REENVIO
            ? EmailTemplates.RESEND
            : EmailTemplates.INVITATION;

    String html = plantilla.render(variables);

    sendEmailViaResend(email.destinatario(), asunto(email.tipo()), html);
  }

  @Override
  public void sendActivationConfirmation(String destinatario, String nombreCompleto) {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("nombreCompleto", nombreCompleto);

    String html = EmailTemplates.CONFIRMATION.render(variables);

    sendEmailViaResend(destinatario, "Tu cuenta de Control's Elector ya esta activa", html);
  }

  /** Envia email por HTTP a Resend API. */
  private void sendEmailViaResend(String to, String subject, String html) {
    String jsonBody =
        String.format(
            """
        {"from":"%s","to":"%s","subject":"%s","html":"%s"}
        """,
            escapeJson(fromEmail), escapeJson(to), escapeJson(subject), escapeJson(html));

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(RESEND_API_URL))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + resendApiKey)
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        log.debugf("Resend email sent successfully to %s", to);
      } else {
        throw new RuntimeException(
            "Resend API returned status " + response.statusCode() + ": " + response.body());
      }
    } catch (Exception e) {
      log.errorf(e, "Failed to send email via Resend to %s", to);
      throw new RuntimeException("Failed to send email via Resend: " + e.getMessage(), e);
    }
  }

  /** Asunto del correo. */
  private String asunto(EmailAEnviar.TipoEmail tipo) {
    return tipo == EmailAEnviar.TipoEmail.REENVIO
        ? "Reenviamos tu invitación a Control's Elector"
        : "Bienvenido a Control's Elector - Activa tu cuenta";
  }

  /** Extrae el usuario sin dominio: "juagarobs@control.ttcs" → "juagarobs". */
  private static String usuarioCorto(String username) {
    if (username == null || username.isBlank()) {
      return "";
    }
    int atIndex = username.indexOf('@');
    return atIndex > 0 ? username.substring(0, atIndex) : username;
  }

  /** Escapa caracteres especiales para JSON. */
  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
