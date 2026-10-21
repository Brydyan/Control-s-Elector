package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.RolPermiso;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.shared.config.Durations;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Canje de refresh token por un par de tokens nuevo, con ROTACION.
 *
 * <p>Port exacto del {@code RefreshTokenUseCase} del backend Spring: mismos mensajes de error,
 * misma busqueda por hash y, sobre todo, misma invariante de seguridad: el refresh token viejo
 * queda invalidado, porque la fila de sesion pasa a guardar el hash del token nuevo. Reusar el
 * token viejo despues del canje devuelve 401.
 *
 * <p>El token plano jamas se guarda: lo que se busca en la tabla {@code sesion} es su hash con
 * pepper ({@link TokenHasher}).
 */
@ApplicationScoped
public class RefreshTokenUseCase {

  @Inject JwtService jwt;

  @Inject TokenHasher tokenHasher;

  @Inject AuthAuditService auditLog;

  private final Duration refreshTtl;

  public RefreshTokenUseCase(@ConfigProperty(name = "app.jwt.refresh-ttl") String refreshTtl) {
    this.refreshTtl = Durations.parse(refreshTtl);
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
    Sesion sesion = Sesion.find("refreshHash", hash).firstResult();
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

    Usuario u = Usuario.find("publicId", publicId).firstResult();
    if (u == null) {
      auditLog.recordRefreshFail(null);
      throw new AuthException("Usuario no existe");
    }
    if (!u.isHabilitado()) {
      auditLog.recordRefreshFail(null);
      throw new AuthException("Usuario deshabilitado");
    }

    List<String> permisos = RolPermiso.authorityStringsByUsuarioId(u.getId());
    JwtService.TokenPair pair =
        jwt.issue(u.getPublicId(), u.getEmail(), u.getRolNombre(), permisos, sesion.getSesionId());

    // ROTACION: la sesion pasa a guardar el hash del refresh token nuevo. El hash del viejo
    // desaparece de la base, asi que el token viejo deja de servir para canjearse.
    sesion.setRefreshHash(tokenHasher.hashOf(pair.refresh()));
    sesion.setUltimoUsoEn(Instant.now());
    sesion.setExpiraEn(Instant.now().plus(refreshTtl));

    auditLog.recordRefresh(u.getId());
    return pair;
  }
}
