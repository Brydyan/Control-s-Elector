package ec.twintailcs.electoral.auditlog.application;

import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/** Registro de auditoria. Mismo contrato que el backend Spring. */
@ApplicationScoped
public class AuditLogService {

  private static final Logger log = Logger.getLogger(AuditLogService.class);

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void recordLogin(Long usuarioId, String ip) {
    recordAuth(usuarioId, "login", "Login successful", ip);
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void recordLoginFail(String email, String ip) {
    recordAuth(null, "login_fail", "Login failed for " + email, ip);
  }

  private void recordAuth(Long usuarioId, String accion, String detalle, String ip) {
    AuditLog entry = new AuditLog();
    entry.setEntidad("auth");
    entry.setEntidadId(usuarioId == null ? 0L : usuarioId);
    entry.setAccion(accion);
    entry.setDetalle("{\"detalle\":\"" + detalle.replace("\"", "\\") + "\"}");
    entry.setIpAddress(ip);
    entry.setRegistradoPor(usuarioId);
    entry.persist();
    log.debugf("Audit %s usuario=%s", accion, usuarioId);
  }
}
