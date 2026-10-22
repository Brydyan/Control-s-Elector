package ec.twintailcs.electoral.acta.application;

import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Registro de auditoria del modulo de actas.
 *
 * <p>Escribe en la misma tabla {@code audit_log} y con el mismo formato de payload que el backend
 * Spring ({@code {"message": ...}}), para que el historial del acta sea indistinguible entre las dos
 * implementaciones.
 *
 * <p>La tabla {@code audit_log} tiene RLS: la escritura esta permitida para cualquier rol (la
 * politica de insercion es {@code WITH CHECK (true)}), pero la lectura solo la pueden hacer
 * COORDINATOR, MASTER y ADMIN.
 */
@ApplicationScoped
public class ActaAuditService {

  /**
   * Registra una accion sobre un acta.
   *
   * @param usuarioId id interno del usuario que hizo la accion (puede ser {@code null})
   * @param accion accion de negocio ({@code REGISTRO_ACTA}, {@code ACTA_EN_DISPUTA}, ...)
   * @param entidad entidad afectada ({@code acta})
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
    entry.setDetalle(detalle == null ? null : payloadJson(detalle));
    entry.setIpAddress(ipAddress != null && !ipAddress.isBlank() ? ipAddress : "sistema");
    entry.setRegistradoPor(usuarioId);
    entry.persist();
  }

  /** Envolve el detalle human-readable en el payload JSON que usa el backend Spring. */
  private String payloadJson(String detalle) {
    return "{\"message\":\"" + escapar(detalle) + "\"}";
  }

  /** Escapa el texto para que sea un valor JSON valido. */
  private String escapar(String texto) {
    return texto.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
