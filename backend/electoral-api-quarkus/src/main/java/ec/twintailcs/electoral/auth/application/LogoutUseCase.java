package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.Sesion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * Logout: revoca la sesion del refresh token.
 *
 * <p>Port exacto del {@code LogoutUseCase} del backend Spring: la revocacion es idempotente (si el
 * token no esta o la sesion ya estaba revocada, no pasa nada y tampoco se falla) y el refresh token
 * nunca se guarda en claro: se busca por su hash con pepper.
 */
@ApplicationScoped
public class LogoutUseCase {

  private static final Logger log = Logger.getLogger(LogoutUseCase.class);

  @Inject TokenHasher tokenHasher;

  @Inject AuthAuditService auditLog;

  @Transactional
  public void execute(String refreshToken) {
    Sesion sesion = Sesion.find("refreshHash", tokenHasher.hashOf(refreshToken)).firstResult();
    if (sesion == null) {
      return;
    }
    sesion.setRevocado(true);
    sesion.setUltimoUsoEn(Instant.now());
    auditLog.recordLogout(sesion.getUsuarioId());
    log.infof("Logout: sesion=%s revocada", sesion.getSesionId());
  }
}
