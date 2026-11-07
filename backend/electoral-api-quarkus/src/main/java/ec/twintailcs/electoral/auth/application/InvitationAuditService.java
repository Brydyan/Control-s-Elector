package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Auditoria del flujo de invitaciones.
 *
 * <p>Es una fachada sobre {@link AuditLogService}: el flujo de invitacion no escribe en la tabla de
 * auditoria por su cuenta, llama a este servicio, que a su vez llama al unico writer del sistema.
 * La razon es la misma que el resto del codigo: la auditoria se escribe siempre por {@code
 * AuditLogService -> JPA -> Postgres}, y no desde el servicio que produce el evento.
 *
 * <p>Los eventos van a {@code audit_log} con {@code entidad = 'INVITACION'}. Esa tabla ya tiene las
 * cuatro columnas que el design pedia para su tabla propia ({@code accion}, {@code detalle} JSONB,
 * {@code timestamp}, la FK al usuario), de modo que la trazabilidad que D10 busca —un rastro
 * completo, consultable por usuario y sin mezclarlo con la tabla de usuarios— esta garantizada sin
 * duplicar el esquema.
 *
 * <p>Cada metodo distingue dos ids que no son lo mismo:
 *
 * <ul>
 *   <li>{@code usuarioId} — el usuario <em>afectado</em> por el evento. Va en {@code entidad_id},
 *       que no tiene clave foranea, y puede ser una fila que la transaccion en curso todavia no
 *       commiteo.
 *   <li>{@code actorId} — quien ejecuto la accion (el MASTER que invita, el propio usuario que se
 *       activa). Va en {@code registrado_por}, que <em>si</em> tiene clave foranea a {@code
 *       usuario}: tiene que ser alguien ya commiteado.
 * </ul>
 *
 * <p>Los nombres de accion tienen que caber en {@code audit_log.accion}, que es VARCHAR(64) desde
 * el changeset 024.
 */
@ApplicationScoped
public class InvitationAuditService {

  /** Entidad bajo la que se registran todos los eventos del flujo. */
  public static final String ENTIDAD = "INVITACION";

  /** Se envio una invitacion y el token quedo vigente. */
  public static final String INVITATION_SENT = "INVITATION_SENT";

  /** Un envio de invitacion fallo (SMTP caido, destinatario invalido, ...). */
  public static final String INVITATION_FAILED = "INVITATION_FAILED";

  /** La invitacion se rechazo antes de enviarse: el usuario no estaba en el estado esperado. */
  public static final String INVITATION_REJECTED = "INVITATION_REJECTED";

  /** Se reenvio una invitacion; el token anterior quedo invalidado. */
  public static final String INVITATION_RESENT = "INVITATION_RESENT";

  /** El usuario completo la activacion y eligio su contrasena. */
  public static final String ACTIVATION_SUCCESS = "ACTIVATION_SUCCESS";

  /** Un intento de activacion fallo (token vencido, contrasena debil, ...). */
  public static final String ACTIVATION_FAILED = "ACTIVATION_FAILED";

  /** Un intento de activacion fue rechazado por el estado de la cuenta. */
  public static final String ACTIVATION_REJECTED = "ACTIVATION_REJECTED";

  /** El login fue rechazado porque la cuenta todavia no se activo. */
  public static final String LOGIN_REJECTED = "LOGIN_REJECTED";

  private final AuditLogService auditLog;

  public InvitationAuditService(AuditLogService auditLog) {
    this.auditLog = auditLog;
  }

  /** Se envio una invitacion. */
  public void logInvitationSent(Long actorId, Long usuarioId, String email, String tokenExpira) {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("email", email);
    if (tokenExpira != null) {
      datos.put("token_exp", tokenExpira);
    }
    auditLog.record(ENTIDAD, usuarioId, actorId, INVITATION_SENT, datos, null);
  }

  /** Un envio fallo. */
  public void logInvitationFailed(Long usuarioId, String email, String error) {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("email", email);
    datos.put("error", error);
    auditLog.record(ENTIDAD, usuarioId, null, INVITATION_FAILED, datos, null);
  }

  /** La invitacion se rechazo antes de enviarse. */
  public void logInvitationRejected(Long actorId, Long usuarioId, String razon) {
    auditLog.record(
        ENTIDAD, usuarioId, actorId, INVITATION_REJECTED, Map.of("error", safe(razon)), null);
  }

  /** Se reenvio una invitacion. */
  public void logInvitationResent(Long actorId, Long usuarioId, String email, String tokenExpira) {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("email", email);
    if (tokenExpira != null) {
      datos.put("token_exp", tokenExpira);
    }
    auditLog.record(ENTIDAD, usuarioId, actorId, INVITATION_RESENT, datos, null);
  }

  /** La activacion termino bien. */
  public void logActivationSuccess(Long usuarioId) {
    auditLog.record(
        ENTIDAD, usuarioId, usuarioId, ACTIVATION_SUCCESS, Map.of("usuario_id", usuarioId), null);
  }

  /** Un intento de activacion fallo. */
  public void logActivationFailed(Long usuarioId, String razon) {
    auditLog.record(
        ENTIDAD, usuarioId, usuarioId, ACTIVATION_FAILED, Map.of("error", safe(razon)), null);
  }

  /** Un intento de activacion fue rechazado por el estado de la cuenta. */
  public void logActivationRejected(Long usuarioId, String razon) {
    auditLog.record(
        ENTIDAD, usuarioId, usuarioId, ACTIVATION_REJECTED, Map.of("error", safe(razon)), null);
  }

  /** El login fue rechazado porque la cuenta sigue pendiente de activacion. */
  public void logLoginRejected(Long usuarioId, String razon) {
    auditLog.record(
        ENTIDAD, usuarioId, usuarioId, LOGIN_REJECTED, Map.of("error", safe(razon)), null);
  }

  private static String safe(String valor) {
    return valor == null ? "" : valor;
  }
}
