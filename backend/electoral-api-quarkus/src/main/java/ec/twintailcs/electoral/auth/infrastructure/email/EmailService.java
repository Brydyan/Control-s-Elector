package ec.twintailcs.electoral.auth.infrastructure.email;

/**
 * Contrato de envio de email.
 *
 * <p>La interfaz existe por la decision D7 del design: el proveedor se cambia sin tocar la logica
 * de negocio. Hoy hay un solo implementador (SMTP); la semana que haga falta un proveedor de API
 * entra como otra implementacion de esta misma interfaz y el codigo que invita no se entera.
 *
 * <p>El metodo declara que puede fallar y no esconde el error: el reintento con backoff y la
 * auditoria de intentos viven por encima de esta interfaz, y para eso el adaptador tiene que dejar
 * escapar la excepcion en vez de tragarsela.
 */
public interface EmailService {

  /**
   * Envia el email de invitacion con el link de activacion.
   *
   * @param email datos del email y del link
   * @throws RuntimeException si el envio falla; el reintento lo aplica quien llama
   */
  void sendInvitationEmail(EmailAEnviar email);

  /**
   * Envia la confirmacion de que la cuenta ya quedo activada.
   *
   * @param destinatario email del usuario
   * @param nombreCompleto nombre del usuario, para el saludo
   */
  void sendActivationConfirmation(String destinatario, String nombreCompleto);
}
