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
  PENDIENTE_INVITACION;

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
