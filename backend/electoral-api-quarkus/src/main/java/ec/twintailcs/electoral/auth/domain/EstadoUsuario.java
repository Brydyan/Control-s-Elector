package ec.twintailcs.electoral.auth.domain;

/**
 * Estado de activacion de una cuenta.
 *
 * <p>El estado vive en la columna {@code usuario.estado_usuario} y es explicito: la alternativa
 * (deducir "pendiente" de {@code activation_token IS NOT NULL}) es fragil, porque un NULL puede
 * significar varias cosas — token consumido, token reemitido, invite dado de baja.
 *
 * <p>Los estados que existen:
 *
 * <ul>
 *   <li>{@code ACTIVO} — la cuenta se puede usar. Es el estado de todas las cuentas existentes y el
 *       unico desde el que se puede iniciar sesion.
 *   <li>{@code PENDIENTE_INVITACION} — la cuenta fue creada pero todavia no eligio su contrasena.
 *       El login responde 403 y el usuario tiene que abrir el link del email.
 *   <li>{@code DELETED_PENDING} — el usuario solicito eliminacion de cuenta (soft-delete). No puede
 *       iniciar sesion. Recuperable durante 7 dias clickeando link en email. Hard-delete automatico
 *       despues 30 dias.
 *   <li>{@code DELETED_PERMANENT} — hard-delete completado. Usuario y datos personales eliminados
 *       (excepto referencias en actas electorales por retension legal). No recuperable.
 * </ul>
 *
 * <p>El bloqueo de una cuenta NO es un estado: lo aporta {@code usuario.habilitado}, que ya
 * deshabilita el acceso sin distinctions. Anadir un estado {@code BLOQUEADO} seria un segundo eje
 * para el mismo hecho y los dos podrian contradecirse.
 */
public enum EstadoUsuario {

  /** La cuenta esta activa y puede iniciar sesion. */
  ACTIVO,

  /** La cuenta fue creada y esta esperando que el usuario active su acceso. */
  PENDIENTE_INVITACION,

  /** Usuario solicito eliminacion de cuenta (soft-delete con 30d grace period). */
  DELETED_PENDING,

  /** Hard-delete permanente completado. Usuario eliminado irreversiblemente. */
  DELETED_PERMANENT;

  /** Nombre del estado tal como se persiste en {@code usuario.estado_usuario}. */
  public String valor() {
    return name();
  }

  /**
   * Convierte el valor persistido en el estado.
   *
   * @throws IllegalArgumentException si el valor no corresponde a un estado conocido
   */
  public static EstadoUsuario desde(String valor) {
    if (valor == null) {
      throw new IllegalArgumentException("estado_usuario no puede ser null");
    }
    for (EstadoUsuario estado : values()) {
      if (estado.name().equals(valor)) {
        return estado;
      }
    }
    throw new IllegalArgumentException("estado_usuario desconocido: " + valor);
  }
}
