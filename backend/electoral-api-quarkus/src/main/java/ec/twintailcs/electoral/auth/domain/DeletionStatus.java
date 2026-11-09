package ec.twintailcs.electoral.auth.domain;

/**
 * Estado de eliminación de una cuenta de usuario.
 *
 * <p>Valores:
 *
 * <ul>
 *   <li>{@code ACTIVO} — la cuenta no esta siendo eliminada.
 *   <li>{@code DELETED_PENDING} — usuario solicito soft-delete, recuperable dentro 7 dias.
 *   <li>{@code DELETED_PERMANENT} — hard-delete permanente completado, no recuperable.
 * </ul>
 *
 * <p>Se persiste en {@code usuario.deletion_status} como String con el nombre del enum. Durante
 * DELETED_PENDING, el usuario no puede iniciar sesion pero puede recuperar dentro 7 dias. Despues
 * 30 dias, el cron job realiza hard-delete y marca DELETED_PERMANENT.
 */
public enum DeletionStatus {

  /** La cuenta no esta siendo eliminada. Estado normal. */
  ACTIVO,

  /**
   * Usuario solicito soft-delete (recuperable).
   *
   * <p>La cuenta no puede iniciar sesion. Recuperable durante 7 dias clickeando link en email
   * (POST /api/user/cancel-deletion/{token}). Hard-delete automatico despues 30 dias.
   */
  DELETED_PENDING,

  /**
   * Hard-delete permanente completado (no recuperable).
   *
   * <p>Usuario y datos personales han sido eliminados, excepto referencias en actas electorales
   * que se retienen por requisito legal (7 años).
   */
  DELETED_PERMANENT;

  /** Nombre del estado tal como se persiste en {@code usuario.deletion_status}. */
  public String valor() {
    return name();
  }

  /**
   * Convierte el valor persistido en el estado.
   *
   * @throws IllegalArgumentException si el valor no corresponde a un estado conocido
   */
  public static DeletionStatus desde(String valor) {
    if (valor == null) {
      throw new IllegalArgumentException("deletion_status no puede ser null");
    }
    for (DeletionStatus status : values()) {
      if (status.name().equals(valor)) {
        return status;
      }
    }
    throw new IllegalArgumentException("deletion_status desconocido: " + valor);
  }
}
