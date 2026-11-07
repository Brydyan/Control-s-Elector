package ec.twintailcs.electoral.auth.application.port;

import ec.twintailcs.electoral.auth.infrastructure.email.EmailAEnviar;

/**
 * Cola de emails de invitacion.
 *
 * <p>Es un puerto: el caso de uso que invita depende de esta interfaz y no de como se delivers el
 * correo. El adaptador de produccion encola y se olvida; un test puede encolar y observar.
 *
 * <p>El contrato tiene una sola regla: {@link #enqueue} no envia. No bloquea, no falla por un error
 * de SMTP y no lanza. Un envio que falla se reintenta en segundo plano y se audita; el endpoint que
 * lo disparo ya respondio 202 y no debe enterarse de que el SMTP estaba caido.
 */
public interface InvitationEmailQueue {

  /**
   * Encola un email de invitacion.
   *
   * <p>No debe bloquear ni lanzar: si la cola esta llena, el email se descarta y queda registrado
   * como fallido, no se propaga el error al llamador.
   *
   * @param email email a enviar
   */
  void enqueue(EmailAEnviar email);

  /** Cantidad de emails que estan en cola todavia. */
  int pendientes();
}
