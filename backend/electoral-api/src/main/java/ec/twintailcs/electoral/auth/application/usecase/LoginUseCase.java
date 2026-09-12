package ec.twintailcs.electoral.auth.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.AuthException;
import ec.twintailcs.electoral.auth.domain.RolPermisoRepository;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.SesionRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginUseCase {

  private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);

  private final UsuarioRepository usuarios;
  private final SesionRepository sesiones;
  private final RolPermisoRepository rolPermisos;
  private final JwtService jwt;
  private final PasswordHasher hasher;
  private final TokenHasher tokenHasher;
  private final AuditLogService auditLog;
  private final Duration refreshTtl;

  public LoginUseCase(
      UsuarioRepository usuarios,
      SesionRepository sesiones,
      RolPermisoRepository rolPermisos,
      JwtService jwt,
      PasswordHasher hasher,
      TokenHasher tokenHasher,
      AuditLogService auditLog,
      @Value("${app.jwt.refresh-ttl:7d}") Duration refreshTtl) {
    this.usuarios = usuarios;
    this.sesiones = sesiones;
    this.rolPermisos = rolPermisos;
    this.jwt = jwt;
    this.hasher = hasher;
    this.tokenHasher = tokenHasher;
    this.auditLog = auditLog;
    this.refreshTtl = refreshTtl;
  }

  @Transactional
  public JwtService.TokenPair execute(
      String email, String password, String ipCliente, String userAgent) {
    Usuario u = usuarios.findByEmail(email).orElse(null);
    if (u == null || !u.isHabilitado() || !hasher.matches(password, u.getPasswordHash())) {
      auditLog.recordLoginFail(email, ipCliente);
      throw new AuthException("Credenciales invalidas");
    }

    String sesionId = UUID.randomUUID().toString();
    List<String> permisos = rolPermisos.findAuthorityStringsByUsuarioId(u.getId());
    JwtService.TokenPair pair =
        jwt.issue(u.getPublicId(), u.getEmail(), u.getRolNombre(), permisos, sesionId);

    String refreshHash = tokenHasher.hashOf(pair.refresh());

    Sesion sesion = new Sesion();
    sesion.setUsuarioId(u.getId());
    sesion.setSesionId(sesionId);
    sesion.setRefreshHash(refreshHash);
    sesion.setIpCliente(ipCliente);
    sesion.setUserAgent(userAgent);
    sesion.setCreadoEn(Instant.now());
    sesion.setUltimoUsoEn(Instant.now());
    sesion.setExpiraEn(Instant.now().plus(refreshTtl));
    sesion.validarDatosObligatorios();
    sesiones.save(sesion);

    auditLog.recordLogin(u.getId(), ipCliente);
    log.info("Login OK: usuario={} sesion={}", u.getEmail(), sesionId);
    return pair;
  }
}
