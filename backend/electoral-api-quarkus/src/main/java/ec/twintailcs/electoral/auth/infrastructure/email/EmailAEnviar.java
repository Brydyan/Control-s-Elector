package ec.twintailcs.electoral.auth.infrastructure.email;

/**
 * Email a enviar desde el flujo de invitaciones.
 *
 * <p>Es un dato, no una instruccion: no sabe de SMTP ni de plantillas. El adaptador decide como
 * llega a destino.
 *
 * <p>Un solo tipo para los tres mensajes del flujo (invitacion, reenvio y confirmacion de
 * activacion) porque los tres viajan por la misma cola, con el mismo rate limit y los mismos
 * reintentos. Un tipo por mensaje habria triplicado la cola y los tres caminos de reintento.
 *
 * @param usuarioId id interno del usuario; lo necesita la auditoria de cada intento
 * @param destinatario email del usuario
 * @param nombre nombre del usuario
 * @param apellido apellido del usuario
 * @param rol rol asignado
 * @param activationLink link con el token de activacion; null en la confirmacion
 * @param tokenExpira momento en que el token deja de valer; null en la confirmacion
 * @param numeroIntento numero de intento del envio (1 en el primer envio, 2 en un reenvio)
 * @param tipo que mensaje es
 */
public record EmailAEnviar(
    Long usuarioId,
    String destinatario,
    String nombre,
    String apellido,
    String rol,
    String activationLink,
    String tokenExpira,
    int numeroIntento,
    TipoEmail tipo) {

  /** Que mensaje es. */
  public enum TipoEmail {
    /** Invitacion inicial con el link de activacion. */
    INVITACION,
    /** Reenvio de la invitacion: el link anterior quedo invalidado. */
    REENVIO,
    /** Confirmacion de que la cuenta ya quedo activa. */
    CONFIRMACION
  }

  /** Nombre completo del usuario, para el saludo de la plantilla. */
  public String nombreCompleto() {
    String n = nombre == null ? "" : nombre.trim();
    String a = apellido == null ? "" : apellido.trim();
    if (n.isEmpty()) {
      return a;
    }
    if (a.isEmpty()) {
      return n;
    }
    return n + " " + a;
  }
}
