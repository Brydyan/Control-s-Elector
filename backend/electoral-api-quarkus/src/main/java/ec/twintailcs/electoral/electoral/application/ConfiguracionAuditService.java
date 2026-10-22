package ec.twintailcs.electoral.electoral.application;

import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Registro de auditoria de la configuracion preelectoral.
 *
 * <p>El {@code AuditLogService} compartido solo expone los eventos de autenticacion, asi que este
 * servicio cubre las mutaciones de la configuracion (partidos, cargos y candidatos) escribiendo en
 * la misma tabla {@code audit_log} y con el mismo formato de payload que el backend Spring: un
 * objeto JSON con la clave {@code message}.
 */
@ApplicationScoped
public class ConfiguracionAuditService {

  /**
   * Registra una mutacion de la configuracion.
   *
   * @param usuarioId id interno del usuario que la hizo (va en {@code registrado_por})
   * @param accion accion de negocio ({@code CREAR_PARTIDO}, {@code ACTUALIZAR_CARGO}, ...)
   * @param entidad entidad afectada ({@code partido}, {@code cargo}, {@code candidato})
   * @param entidadId id interno de la entidad afectada
   * @param detalle detalle legible de la operacion
   * @param ipAddress IP del cliente; si viene vacia se guarda {@code sistema}
   */
  @Transactional
  public void registrarConIp(
      Long usuarioId,
      String accion,
      String entidad,
      Long entidadId,
      String detalle,
      String ipAddress) {
    AuditLog entry = new AuditLog();
    entry.setEntidad(entidad);
    entry.setEntidadId(entidadId);
    entry.setAccion(accion);
    entry.setDetalle(toJsonPayload(detalle));
    entry.setIpAddress(ipAddress != null && !ipAddress.isBlank() ? ipAddress : "sistema");
    entry.setRegistradoPor(usuarioId);
    entry.persist();
  }

  /**
   * Envuelve un detalle legible en un payload JSON para la columna {@code audit_log.detalle}
   * (JSONB). Null se mantiene como SQL NULL, no como un objeto vacio.
   */
  private static String toJsonPayload(String detalle) {
    if (detalle == null) {
      return null;
    }
    return "{\"message\":\"" + escapar(detalle) + "\"}";
  }

  /** Escapa el detalle para que el JSON sea valido. */
  private static String escapar(String valor) {
    return valor.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
