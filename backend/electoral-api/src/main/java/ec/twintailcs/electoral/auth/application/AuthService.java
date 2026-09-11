package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.RolPermisoRepository;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.SesionRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica de login, refresh y logout.
 *
 * <p>El flow canonico es: 1. login: valida credenciales, crea Sesion, emite tokens. 2. access: el
 * JwtAuthFilter lo valida en cada request. 3. refresh: cuando expira el access, el cliente llama
 * /auth/refresh con el refresh token. Si matchea el hash y la Sesion esta activa, se emite un nuevo
 * par y se rota el refresh (la Sesion apuntando al nuevo hash, el viejo ya no sirve). Esto limita
 * el blast radius de un refresh robado a un solo uso. 4. logout: marca la Sesion como revocada.
 * Cualquier refresh falla. El access sigue valido hasta su TTL (max 15m). Limpiamos el ultimo uso
 * de la Sesion para auditoria.
 *
 * <p>BCrypt vs HMAC para refresh: - Para el refresh token, no usamos BCrypt (es lento). Usamos
 * HMAC-SHA-256 con el mismo secret que JWT, luego base64url. Es suficiente porque el token vive
 * poco tiempo y la rotacion es agresiva.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UsuarioRepository usuarios;
  private final SesionRepository sesiones;
  private final RolPermisoRepository rolPermisos;
  private final JwtService jwt;
  private final PasswordHasher hasher;
  private final String refreshPepper;
  private final ec.twintailcs.electoral.auditlog.application.AuditLogService auditLog;

  public AuthService(
      UsuarioRepository usuarios,
      SesionRepository sesiones,
      RolPermisoRepository rolPermisos,
      JwtService jwt,
      PasswordHasher hasher,
      ec.twintailcs.electoral.auditlog.application.AuditLogService auditLog,
      @org.springframework.beans.factory.annotation.Value(
              "${app.jwt.refresh-pepper:control-elector-refresh}")
          String refreshPepper) {
    this.usuarios = usuarios;
    this.sesiones = sesiones;
    this.rolPermisos = rolPermisos;
    this.jwt = jwt;
    this.hasher = hasher;
    this.refreshPepper = refreshPepper;
    this.auditLog = auditLog;
  }

  /** Login: valida credenciales y emite tokens. */
  @Transactional
  public JwtService.TokenPair login(
      String email, String password, String ipCliente, String userAgent) {
    Usuario u = usuarios.findByEmail(email).orElse(null);
    if (u == null || !u.isHabilitado() || !hasher.matches(password, u.getPasswordHash())) {
      auditLog.recordLoginFail(email, ipCliente);
      // 401 generico para no filtrar si el user existe o no.
      throw new AuthException("Credenciales invalidas");
    }

    // crea sesion persistente
    String sesionId = UUID.randomUUID().toString();
    Sesion sesion = new Sesion();
    sesion.setUsuarioId(u.getId());
    sesion.setSesionId(sesionId);
    sesion.setRefreshHash("placeholder-set-after-token-issued");
    sesion.setIpCliente(ipCliente);
    sesion.setUserAgent(userAgent);
    sesion.setCreadoEn(Instant.now());
    sesion.setUltimoUsoEn(Instant.now());
    sesion.setExpiraEn(Instant.now().plus(Duration.ofDays(7)));
    sesiones.save(sesion);

    List<String> permisos = rolPermisos.findAuthorityStringsByUsuarioId(u.getId());
    JwtService.TokenPair pair =
        jwt.issue(u.getPublicId(), u.getEmail(), u.getRolNombre(), permisos, sesionId);

    // guarda hash del refresh (nunca el token plano)
    String refreshHash = hashOf(pair.refresh());
    sesion.setRefreshHash(refreshHash);
    sesiones.save(sesion);

    auditLog.recordLogin(u.getId(), ipCliente);
    log.info("Login OK: usuario={} sesion={}", u.getEmail(), sesionId);
    return pair;
  }

  /** Refresh: valida el refresh token, emite uno nuevo, rota. */
  @Transactional
  public JwtService.TokenPair refresh(String refreshToken) {
    // El refresh es un JWT firmado por nosotros. parseRefresh valida firma,
    // exp, iss y typ=refresh en un solo paso.
    JwtService.ParsedJwt parsed;
    try {
      parsed = jwt.parseRefresh(refreshToken);
    } catch (JwtService.InvalidJwtException e) {
      auditLog.recordRefreshFail(null);
      throw new AuthException(e.getMessage());
    }

    String hash = hashOf(refreshToken);
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
    // Usuario deshabilitado: NO renovamos sesion.
    if (!u.isHabilitado()) {
      auditLog.recordRefreshFail(null);
      throw new AuthException("Usuario deshabilitado");
    }

    // emite nuevo par con el mismo sesionId (revocacion explicita del
    // refresh anterior: cambia el hash guardado, el viejo deja de servir)
    List<String> permisos = rolPermisos.findAuthorityStringsByUsuarioId(u.getId());
    JwtService.TokenPair pair =
        jwt.issue(u.getPublicId(), u.getEmail(), u.getRolNombre(), permisos, sesion.getSesionId());
    sesion.setRefreshHash(hashOf(pair.refresh()));
    sesion.setUltimoUsoEn(Instant.now());
    sesiones.save(sesion);

    auditLog.recordRefresh(u.getId());
    return pair;
  }

  /** Logout: revoca la sesion (los refreshes futuros fallan). */
  @Transactional
  public void logout(String refreshToken) {
    Optional<Sesion> opt = sesiones.findByRefreshHash(hashOf(refreshToken));
    opt.ifPresent(
        s -> {
          s.setRevocado(true);
          s.setUltimoUsoEn(Instant.now());
          sesiones.save(s);
          auditLog.recordLogout(s.getUsuarioId());
          log.info("Logout: sesion={} revocada", s.getSesionId());
        });
  }

  private String hashOf(String token) {
    try {
      // HMAC-SHA256(token + pepper) en lugar del token plano.
      // No es BCrypt porque el refresh vive poco y rotamos rapido.
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(
          new javax.crypto.spec.SecretKeySpec(
              refreshPepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
      throw new IllegalStateException("HmacSHA256 indisponible", e);
    }
  }

  /** 401 generico para no filtrar si el user existe o no. */
  public static class AuthException extends RuntimeException {
    public AuthException(String msg) {
      super(msg);
    }
  }
}
