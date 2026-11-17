package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.AccesoNoPermitidoException;
import ec.twintailcs.electoral.auth.domain.RolPermiso;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Autenticacion por email y password.
 *
 * <p>Port exacto del {@code LoginUseCase} del backend Spring: mismos mensajes de error, misma
 * creacion de sesion y misma auditoria.
 */
@ApplicationScoped
public class LoginUseCase {

  @Inject JwtService jwt;

  @Inject PasswordHasher hasher;

  @Inject TokenHasher tokenHasher;

  @Inject AuthAuditService auditLog;

  @Inject InvitationAuditService invitationAudit;

  @Transactional
  public JwtService.TokenPair execute(
      String email, String password, String ipCliente, String userAgent) {
    Usuario u =
        Usuario.find("email", email == null ? "" : email.trim().toLowerCase()).firstResult();
    if (u == null || !u.isHabilitado() || !hasher.matches(password, u.getPasswordHash())) {
      auditLog.recordLoginFail(email, ipCliente);
      throw new AuthException("Credenciales invalidas");
    }

    // Una cuenta que todavia no eligio su contrasena no puede entrar. El chequeo va
    // DESPUES del de la contrasena a proposito: si fuera antes, el mensaje "no activaste
    // tu cuenta" confirmaria que el email existe aunque la contrasena sea incorrecta.
    // Asi, solo quien ya conoce la contrasena se entera del estado de la cuenta.
    if (!u.estaActivo()) {
      invitationAudit.logLoginRejected(u.getId(), "usuario_pendiente_activacion");
      throw new AccesoNoPermitidoException(
          "Usuario aún no ha activado su cuenta. Revisa tu email.");
    }

    // Cuenta marcada para eliminacion (soft-delete): no puede entrar
    // El usuario puede recuperarla clickeando el link en el email (7 dias)
    if (u.estaPendienteEliminacion()) {
      invitationAudit.logLoginRejected(u.getId(), "cuenta_marcada_eliminacion");
      throw new AccesoNoPermitidoException(
          "Tu cuenta está marcada para eliminación. Revisa tu email para recuperarla.");
    }

    String sesionId = UUID.randomUUID().toString();
    List<String> permisos = RolPermiso.authorityStringsByUsuarioId(u.getId());
    JwtService.TokenPair pair =
        jwt.issue(u.getPublicId(), u.getEmail(), u.getRolNombre(), permisos, sesionId);

    Sesion sesion = new Sesion();
    sesion.setUsuarioId(u.getId());
    sesion.setSesionId(sesionId);
    sesion.setRefreshHash(tokenHasher.hashOf(pair.refresh()));
    sesion.setIpCliente(ipCliente);
    sesion.setUserAgent(userAgent);
    sesion.setCreadoEn(Instant.now());
    sesion.setUltimoUsoEn(Instant.now());
    sesion.setExpiraEn(Instant.now().plus(pair.refreshTtl()));
    sesion.persist();
    auditLog.recordLogin(u.getId(), ipCliente);
    return pair;
  }
}
