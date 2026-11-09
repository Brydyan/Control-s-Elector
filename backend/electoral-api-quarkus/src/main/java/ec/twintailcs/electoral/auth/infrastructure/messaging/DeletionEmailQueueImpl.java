package ec.twintailcs.electoral.auth.infrastructure.messaging;

import ec.twintailcs.electoral.auth.application.port.DeletionEmailQueue;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

/**
 * Implementación de la cola de emails de eliminación de cuenta.
 *
 * <p>Por ahora es un stub que solo registra en los logs. En una implementación real encolaría
 * mensajes en un broker (Kafka, RabbitMQ) o un worker asincrónico.
 */
@ApplicationScoped
public class DeletionEmailQueueImpl implements DeletionEmailQueue {

  @Override
  public void enqueueCancellationLink(
      String email, String nombre, String recoveryToken, Instant recoveryDeadline) {
    Log.info(
        "Deletion cancellation link email queued for "
            + email
            + " with deadline "
            + recoveryDeadline);
    // TODO: implementar envío real via SES/SMTP o encoado en Kafka
  }

  @Override
  public void enqueueCancellationConfirmation(String email, String nombre) {
    Log.info("Deletion cancellation confirmation email queued for " + email);
    // TODO: implementar envío real via SES/SMTP o encolado en Kafka
  }
}
