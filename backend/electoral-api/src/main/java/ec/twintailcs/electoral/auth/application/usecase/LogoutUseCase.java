package ec.twintailcs.electoral.auth.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.SesionRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutUseCase {

  private static final Logger log = LoggerFactory.getLogger(LogoutUseCase.class);

  private final SesionRepository sesiones;
  private final TokenHasher tokenHasher;
  private final AuditLogService auditLog;

  public LogoutUseCase(
      SesionRepository sesiones, TokenHasher tokenHasher, AuditLogService auditLog) {
    this.sesiones = sesiones;
    this.tokenHasher = tokenHasher;
    this.auditLog = auditLog;
  }

  @Transactional
  public void execute(String refreshToken) {
    Optional<Sesion> opt = sesiones.findByRefreshHash(tokenHasher.hashOf(refreshToken));
    opt.ifPresent(
        s -> {
          s.setRevocado(true);
          s.setUltimoUsoEn(Instant.now());
          sesiones.save(s);
          auditLog.recordLogout(s.getUsuarioId());
          log.info("Logout: sesion={} revocada", s.getSesionId());
        });
  }
}
