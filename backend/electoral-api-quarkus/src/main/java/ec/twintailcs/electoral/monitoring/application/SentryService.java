package ec.twintailcs.electoral.monitoring.application;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Rastreo de eventos personalizados en Sentry: login, acceso a datos, exportaciones.
 *
 * <p>Se encarga de enviar eventos configurables a Sentry para auditoría y cumplimiento normativo
 * (compliance). Todos los eventos incluyen contexto (usuario, timestamp) y etiquetas para filtrado.
 */
@ApplicationScoped
public class SentryService {

  /**
   * Rastrear un intento de login.
   *
   * @param userId ID del usuario (o null si es un email desconocido)
   * @param success true si el login fue exitoso, false si falló
   */
  public void trackLogin(String userId, boolean success) {
    SentryEvent event = new SentryEvent();
    event.setLevel(success ? SentryLevel.INFO : SentryLevel.WARNING);
    Message msg = new Message();
    msg.setMessage("login " + (success ? "success" : "failure"));
    event.setMessage(msg);
    event.setTag("event_type", "auth");
    if (userId != null) {
      event.setTag("user_id", userId);
    }
    Sentry.captureEvent(event);
  }

  /**
   * Rastrear acceso a actas (lectura, descarga, exportación).
   *
   * @param userId ID del usuario
   * @param actaId ID del acta
   * @param action tipo de acción (read, download, export)
   */
  public void trackActaAccess(String userId, String actaId, String action) {
    SentryEvent event = new SentryEvent();
    event.setLevel(SentryLevel.INFO);
    Message msg = new Message();
    msg.setMessage("data_access: acta " + action);
    event.setMessage(msg);
    event.setTag("event_type", "data_access");
    event.setTag("resource_type", "acta");
    event.setTag("action", action);
    event.setTag("user_id", userId);
    event.setTag("acta_id", actaId);
    Sentry.captureEvent(event);
  }

  /**
   * Rastrear acceso a resultados.
   *
   * @param userId ID del usuario
   * @param juntaId ID de la junta
   * @param action tipo de acción (read, download, export)
   */
  public void trackResultadosAccess(String userId, String juntaId, String action) {
    SentryEvent event = new SentryEvent();
    event.setLevel(SentryLevel.INFO);
    Message msg = new Message();
    msg.setMessage("data_access: resultados " + action);
    event.setMessage(msg);
    event.setTag("event_type", "data_access");
    event.setTag("resource_type", "resultados");
    event.setTag("action", action);
    event.setTag("user_id", userId);
    event.setTag("junta_id", juntaId);
    Sentry.captureEvent(event);
  }

  /**
   * Rastrear exportación de datos.
   *
   * @param userId ID del usuario
   * @param format formato de exportación (pdf, csv, etc)
   * @param recordCount número de registros exportados
   */
  public void trackExport(String userId, String format, int recordCount) {
    SentryEvent event = new SentryEvent();
    event.setLevel(SentryLevel.INFO);
    Message msg = new Message();
    msg.setMessage("export: " + format);
    event.setMessage(msg);
    event.setTag("event_type", "data_export");
    event.setTag("format", format);
    event.setTag("user_id", userId);
    event.setTag("record_count", String.valueOf(recordCount));
    Sentry.captureEvent(event);
  }

  /**
   * Rastrear un error crítico de aplicación.
   *
   * @param errorType tipo de error
   * @param message mensaje descriptivo
   * @param userId ID del usuario afectado (nullable)
   */
  public void trackCriticalError(String errorType, String message, String userId) {
    SentryEvent event = new SentryEvent();
    event.setLevel(SentryLevel.ERROR);
    Message msg = new Message();
    msg.setMessage(message);
    event.setMessage(msg);
    event.setTag("event_type", "critical_error");
    event.setTag("error_type", errorType);
    if (userId != null) {
      event.setTag("user_id", userId);
    }
    Sentry.captureEvent(event);
  }
}
