package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.UUID;

/**
 * Resultado de un envio de invitaciones.
 *
 * @param sentCount cuantas invitaciones quedaron encoladas
 * @param failures invitaciones que no se pudieron encolar, con la razon
 */
@RegisterForReflection
public record EnviarInvitacionResponse(int sentCount, List<InvitacionFailure> failures) {

  /** Una invitacion que no se pudo enviar. */
  public record InvitacionFailure(UUID usuarioId, String razon) {}
}
