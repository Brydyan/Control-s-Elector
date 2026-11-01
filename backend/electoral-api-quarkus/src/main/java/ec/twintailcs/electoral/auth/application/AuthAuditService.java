package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Auditoria de los eventos del modulo de autenticacion.
 *
 * <p>Es el unico punto de entrada del modulo para dejar rastro: login, refresh y logout.
 *
 * <p><b>Deuda conocida</b>: {@code login} y {@code login_fail} se delegan en el {@link
 * AuditLogService} compartido, que es el que debe tener la propiedad de la escritura. {@code
 * refresh}, {@code refresh_fail} y {@code logout} todavia NO existen en ese servicio compartido (el
 * paquete {@code auditlog} esta fuera de la superficie de edicion de esta fase de la migracion),
 * asi que aqui se escribe la fila con la misma forma. Cuando la superficie se abra, estos tres
 * metodos tienen que mudarse a {@code AuditLogService} y esta clase quedar como una delegacion
 * pura: no hay que cambiar ningun llamador.
 */
@ApplicationScoped
public class AuthAuditService {

  @Inject AuditLogService auditLog;

  /** Login exitoso. */
  public void recordLogin(Long usuarioId, String ip) {
    auditLog.recordLogin(usuarioId, ip);
  }

  /** Login fallido. Se registra el email intentado, nunca la password. */
  public void recordLoginFail(String email, String ip) {
    auditLog.recordLoginFail(email, ip);
  }

  /** Refresh exitoso: el refresh token rotó. */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void recordRefresh(Long usuarioId) {
    write(usuarioId, "refresh", "Token refreshed", null);
  }

  /** Refresh fallido: token ausente, expirado, revocado o ya canjeado. */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void recordRefreshFail(String ip) {
    write(null, "refresh_fail", "Refresh failed", ip);
  }

  /** Logout: la sesion quedo revocada. */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void recordLogout(Long usuarioId) {
    write(usuarioId, "logout", "Logout successful", null);
  }

  /**
   * Escribe la fila de auditoria de un evento de autenticacion.
   *
   * <p>Misma forma que escribe el {@code AuditLogService} compartido: entidad {@code auth}, detalle
   * en JSON e IP del cliente.
   */
  private void write(Long usuarioId, String accion, String detalle, String ip) {
    AuditLog entry = new AuditLog();
    entry.setEntidad("auth");
    entry.setEntidadId(usuarioId == null ? 0L : usuarioId);
    entry.setAccion(accion);
    entry.setDetalle("{\"detalle\":\"" + detalle + "\"}");
    entry.setIpAddress(ip);
    entry.setRegistradoPor(usuarioId);
    entry.persist();
  }
}
