package ec.twintailcs.electoral.auth.application.port;

import java.time.Instant;

/**
 * Cola de emails para eliminación de cuenta.
 *
 * <p>Es un puerto: los casos de uso de eliminación dependen de esta interfaz y no de cómo se
 * entrega el correo. El adaptador de producción encola y se olvida; un test puede encolar y
 * observar.
 *
 * <p>No envía, no bloquea, no falla por SMTP. Los errores de envío se reintentan en segundo plano
 * y se auditan.
 */
public interface DeletionEmailQueue {

  /**
   * Encola el email de confirmación de eliminación con link de recuperación.
   *
   * @param email email del usuario
   * @param nombre nombre del usuario
   * @param recoveryToken token para clickear en "Cancelar eliminación"
   * @param recoveryDeadline fecha límite para cancelar (T+7d)
   */
  void enqueueCancellationLink(
      String email, String nombre, String recoveryToken, Instant recoveryDeadline);

  /**
   * Encola el email de confirmación de cancelación de eliminación.
   *
   * @param email email del usuario
   * @param nombre nombre del usuario
   */
  void enqueueCancellationConfirmation(String email, String nombre);
}
