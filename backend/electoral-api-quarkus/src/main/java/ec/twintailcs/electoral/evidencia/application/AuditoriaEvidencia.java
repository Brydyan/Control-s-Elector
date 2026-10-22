package ec.twintailcs.electoral.evidencia.application;

import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Registro de auditoria del modulo de evidencias.
 *
 * <p><b>Por que escribe la entidad y no usa el {@code AuditLogService}.</b> El servicio de
 * auditoria del backend Quarkus todavia no expone el metodo generico {@code registrar(...)} que
 * usa el backend Spring (solo tiene los de sesion), y el modulo de auditoria esta fuera de la
 * superficie de edicion de este modulo. Escribir la entidad desde aca deja el mismo rastro en la
 * base (misma tabla, misma accion, mismo detalle y mismo formato JSON) y no cambia ninguna
 * invariante: el detalle human-readable se envuelve en el payload {@code {"message": ...}} que usa
 * Spring.
 *
 * <p>Cuando se porte el {@code AuditLogService} completo, esta clase se borra y las llamadas pasan
 * por el servicio.
 */
@ApplicationScoped
public class AuditoriaEvidencia {

  /**
   * Registra una accion sobre una evidencia.
   *
   * @param usuarioId id interno del usuario que hizo la accion (puede ser {@code null})
   * @param accion accion registrada (mismos valores que usa el backend Spring)
   * @param evidenciaId id interno de la evidencia
   * @param detalle detalle human-readable; se envuelve en un payload JSON
   * @param ip direccion IP del cliente (o {@code sistema})
   */
  public void registrar(
      Long usuarioId, String accion, Long evidenciaId, String detalle, String ip) {
    AuditLog entrada = new AuditLog();
    entrada.setEntidad("evidencia");
    entrada.setEntidadId(evidenciaId == null ? 0L : evidenciaId);
    entrada.setAccion(accion);
    entrada.setDetalle(detalle == null ? null : payloadJson(detalle));
    entrada.setIpAddress(ip == null || ip.isBlank() ? "sistema" : ip);
    entrada.setRegistradoPor(usuarioId);
    entrada.persist();
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
