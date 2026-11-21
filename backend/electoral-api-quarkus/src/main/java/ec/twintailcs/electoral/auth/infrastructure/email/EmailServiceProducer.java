package ec.twintailcs.electoral.auth.infrastructure.email;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import ec.twintailcs.electoral.auth.infrastructure.email.SmtpProvider;
import ec.twintailcs.electoral.auth.infrastructure.email.ResendProvider;

/**
 * Productor que selecciona la implementación de EmailService basado en la configuración.
 *
 * <p>Resuelve la ambigüedad de CDI usando @Qualifier: cada implementación tiene un qualifier
 * (@SmtpProvider, @ResendProvider). El productor inyecta ambas con sus qualifiers, luego elige cuál
 * retornar basado en la configuración. El app code inyecta EmailService sin qualifier, viendo solo
 * el método productor como candidato (sin ambigüedad).
 */
@ApplicationScoped
public class EmailServiceProducer {

  /**
   * Provee la implementación correcta de EmailService basado en {@code app.email.provider}.
   *
   * @param provider nombre del proveedor de email configurado (smtp o resend)
   * @param smtp instancia de SmtpEmailProvider, inyectada con @SmtpProvider
   * @param resend instancia de ResendEmailProvider, inyectada con @ResendProvider
   * @return instancia correcta de EmailService
   * @throws IllegalArgumentException si el proveedor no es reconocido
   */
  @Produces
  public EmailService produceEmailService(
      @ConfigProperty(name = "app.email.provider") String provider,
      @SmtpProvider SmtpEmailProvider smtp,
      @ResendProvider ResendEmailProvider resend) {
    return switch (provider) {
      case "resend" -> resend;
      case "smtp" -> smtp;
      default ->
          throw new IllegalArgumentException(
              "Unrecognized email provider: " + provider + ". Must be 'smtp' or 'resend'.");
    };
  }
}
