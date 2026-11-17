package ec.twintailcs.electoral.auditlog.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Map;
import org.jboss.logging.Logger;

/** Registro de auditoria. Mismo contrato que el backend Spring. */
@ApplicationScoped
public class AuditLogService {

  private static final Logger log = Logger.getLogger(AuditLogService.class);

  private final ObjectMapper objectMapper;

  public AuditLogService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void recordLogin(Long usuarioId, String ip) {
    recordAuth(usuarioId, "login", "Login successful", ip);
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void recordLoginFail(String email, String ip) {
    recordAuth(null, "login_fail", "Login failed for " + email, ip);
  }

  /**
   * Registra un evento de auditoria arbitrario.
   *
   * <p>Es la puerta de entrada generica: todo lo que no sea login pasa por aca, de modo que exista
   * un solo camino de escritura sobre {@code audit_log}. Un segundo camino —escribir la tabla de
   * auditoria desde el servicio que produce el evento— es la forma habitual de que el rastro se
   * desincronice del estado real.
   *
   * <p>El detalle se serializa con Jackson y no concatenando cadenas: el campo es un JSONB y
   * concatenar texto a mano rompe en cuanto un valor trae comillas o un salto de linea.
   *
   * @param entidad nombre de la entidad auditada ({@code INVITACION}, {@code ACTA}, ...)
   * @param entidadId id interno de la entidad auditada. No tiene clave foranea: puede apuntar a una
   *     fila que la transaccion en curso todavia no commiteo.
   * @param registradoPor id del usuario que hizo la accion. Es una clave foranea a {@code usuario},
   *     asi que tiene que ser alguien que ya este commiteado — nunca la entidad que se acaba de
   *     crear en la misma transaccion.
   * @param accion que paso; hasta 64 caracteres, es lo que admite la columna
   * @param datos detalle estructurado del evento; null o vacio se guarda como null
   * @param ip direccion del cliente, si la hay
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void record(
      String entidad,
      Long entidadId,
      Long registradoPor,
      String accion,
      Map<String, Object> datos,
      String ip) {
    AuditLog entry = new AuditLog();
    entry.setEntidad(entidad);
    entry.setEntidadId(entidadId == null ? 0L : entidadId);
    entry.setAccion(accion);
    entry.setDetalle(serialize(datos));
    entry.setIpAddress(ip);
    entry.setRegistradoPor(registradoPor);
    entry.persist();
    log.debugf("Audit %s %s id=%s", entidad, accion, entidadId);
  }

  private String recordAuth(Long usuarioId, String accion, String detalle, String ip) {
    AuditLog entry = new AuditLog();
    entry.setEntidad("auth");
    entry.setEntidadId(usuarioId == null ? 0L : usuarioId);
    entry.setAccion(accion);
    entry.setDetalle("{\"detalle\":\"" + detalle.replace("\"", "\\") + "\"}");
    entry.setIpAddress(ip);
    entry.setRegistradoPor(usuarioId);
    entry.persist();
    log.debugf("Audit %s usuario=%s", accion, usuarioId);
    return detalle;
  }

  /** Serializa el detalle a JSON. Un valor no serializable no puede romper la auditoria. */
  private String serialize(Map<String, Object> datos) {
    if (datos == null || datos.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(datos);
    } catch (JsonProcessingException e) {
      log.warnf("No se pudo serializar el detalle de auditoria: %s", e.getMessage());
      return null;
    }
  }
}
