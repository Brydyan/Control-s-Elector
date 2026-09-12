package ec.twintailcs.electoral.auth.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.domain.AuthException;
import ec.twintailcs.electoral.auth.domain.RolPermisoRepository;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.SesionRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenUseCase {

  private final UsuarioRepository usuarios;
  private final SesionRepository sesiones;
  private final RolPermisoRepository rolPermisos;
  private final JwtService jwt;
  private final TokenHasher tokenHasher;
  private final AuditLogService auditLog;

  public RefreshTokenUseCase(
      UsuarioRepository usuarios,
      SesionRepository sesiones,
      RolPermisoRepository rolPermisos,
      JwtService jwt,
      TokenHasher tokenHasher,
      AuditLogService auditLog) {
    this.usuarios = usuarios;
    this.sesiones = sesiones;
    this.rolPermisos = rolPermisos;
    this.jwt = jwt;
    this.tokenHasher = tokenHasher;
    this.auditLog = auditLog;
  }

  @Transactional
  public JwtService.TokenPair execute(String refreshToken) {
    JwtService.ParsedJwt parsed;
    try {
      parsed = jwt.parseRefresh(refreshToken);
    } catch (JwtService.InvalidJwtException e) {
      auditLog.recordRefreshFail(null);
      throw new AuthException(e.getMessage());
    }

    String hash = tokenHasher.hashOf(refreshToken);
    Sesion sesion = sesiones.findByRefreshHash(hash).orElse(null);
    if (sesion == null || !sesion.activa()) {
      auditLog.recordRefreshFail(null);
      throw new AuthException("Refresh token invalido o ya usado");
    }

    UUID publicId;
    try {
      publicId = UUID.fromString(parsed.subject());
    } catch (IllegalArgumentException | NullPointerException e) {
      auditLog.recordRefreshFail(null);
      throw new AuthException("Token invalido");
    }
    Usuario u = usuarios.findByPublicId(publicId).orElse(null);
    if (u == null) {
      auditLog.recordRefreshFail(null);
      throw new AuthException("Usuario no existe");
    }
    if (!u.isHabilitado()) {
      auditLog.recordRefreshFail(null);
      throw new AuthException("Usuario deshabilitado");
    }

    List<String> permisos = rolPermisos.findAuthorityStringsByUsuarioId(u.getId());
    JwtService.TokenPair pair =
        jwt.issue(u.getPublicId(), u.getEmail(), u.getRolNombre(), permisos, sesion.getSesionId());
    sesion.setRefreshHash(tokenHasher.hashOf(pair.refresh()));
    sesion.setUltimoUsoEn(Instant.now());
    sesiones.save(sesion);

    auditLog.recordRefresh(u.getId());
    return pair;
  }
}
