package ec.twintailcs.electoral.auth.infrastructure.email;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Productor que selecciona la implementación de EmailService basado en la configuración.
 *
 * <p>Evita la ambigüedad de CDI cuando hay múltiples implementaciones disponibles: solo el
 * productor es un bean CDI de scope @ApplicationScoped, los providers no.
 */
@ApplicationScoped
public class EmailServiceProducer {

  /**
   * Provee la implementación correcta de EmailService basado en {@code app.email.provider}.
   *
   * @param provider nombre del proveedor de email configurado (smtp o resend)
   * @param smtp instancia de SmtpEmailProvider (inyectada por CDI)
   * @param resend instancia de ResendEmailProvider (inyectada por CDI)
   * @return instancia correcta de EmailService
   * @throws IllegalArgumentException si el proveedor no es reconocido
   */
  @Produces
  public EmailService produceEmailService(
      @ConfigProperty(name = "app.email.provider") String provider,
      SmtpEmailProvider smtp,
      ResendEmailProvider resend) {
    return switch (provider) {
      case "resend" -> resend;
      case "smtp" -> smtp;
      default ->
          throw new IllegalArgumentException(
              "Unrecognized email provider: " + provider + ". Must be 'smtp' or 'resend'.");
    };
  }
}
