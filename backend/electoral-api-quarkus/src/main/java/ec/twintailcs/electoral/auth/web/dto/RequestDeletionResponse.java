package ec.twintailcs.electoral.auth.web.dto;

import java.time.Instant;

/**
 * Respuesta de POST /api/user/request-deletion.
 *
 * <p>Información sobre el soft-delete: deadlines de recuperación (7d) y hard-delete (30d).
 */
public class RequestDeletionResponse {

  /** Fecha hasta la cual el usuario puede recuperar su cuenta (T+7d). */
  public Instant recoveryDeadline;

  /** Fecha en que se ejecutará hard-delete permanente (T+30d). */
  public Instant hardDeleteDeadline;

  public RequestDeletionResponse(Instant recoveryDeadline, Instant hardDeleteDeadline) {
    this.recoveryDeadline = recoveryDeadline;
    this.hardDeleteDeadline = hardDeleteDeadline;
  }
}
