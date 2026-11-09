package ec.twintailcs.electoral.auth.domain;

/**
 * Estado de la invitacion de una cuenta, para el panel de administracion.
 *
 * <p>No es el estado de la cuenta ({@link EstadoUsuario}) sino el de la invitacion: una cuenta
 * puede estar activa y a la vez tener una invitacion vieja sin activar, y el operador necesita
 * distinguir "esta esperando el correo" de "ya entro".
 *
 * <ul>
 *   <li>{@code CREATED} — la cuenta existe pero nunca se le envio invitacion.
 *   <li>{@code INVITED} — la invitacion salio y sigue vigente (24 horas).
 *   <li>{@code EXPIRED} — la invitacion salio pero vencio sin que nadie la activara.
 *   <li>{@code ACTIVE} — la cuenta se activo.
 *   <li>{@code DISABLED} — la cuenta esta deshabilitada.
 * </ul>
 */
public enum EstadoInvitacion {

  /** La cuenta existe, sin invitacion enviada. */
  CREATED,

  /** Invitacion enviada y vigente. */
  INVITED,

  /** Invitacion enviada y vencida. */
  EXPIRED,

  /** La cuenta ya se activo. */
  ACTIVE,

  /** La cuenta esta deshabilitada. */
  DISABLED;

  /** Nombre tal como se persiste y se viaja en el cable. */
  public String valor() {
    return name();
  }

  /**
   * Convierte el valor persistido en el estado.
   *
   * @throws IllegalArgumentException si el valor no corresponde a un estado conocido
   */
  public static EstadoInvitacion desde(String valor) {
    if (valor == null) {
      throw new IllegalArgumentException("estado de invitacion no puede ser null");
    }
    for (EstadoInvitacion estado : values()) {
      if (estado.name().equals(valor)) {
        return estado;
      }
    }
    throw new IllegalArgumentException("estado de invitacion desconocido: " + valor);
  }
}
