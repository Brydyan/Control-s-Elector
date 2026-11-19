package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Servicio para cambiar contraseña del usuario.
 *
 * <p>Validaciones:
 * - Contraseña actual debe ser correcta
 * - Nueva contraseña debe cumplir requisitos de complejidad
 * - Nueva contraseña no puede ser igual a la anterior
 * - Ambas contraseñas deben coincidir
 */
@ApplicationScoped
public class PasswordChangeService {

  private static final Logger log = Logger.getLogger(PasswordChangeService.class);

  @Inject PasswordHasher passwordHasher;
  @Inject PasswordValidationService passwordValidation;
  @Inject AuditLogService auditLog;

  /**
   * Cambia la contraseña del usuario.
   *
   * @param usuarioId ID del usuario
   * @param currentPassword contraseña actual en claro
   * @param newPassword nueva contraseña en claro
   * @param confirmPassword confirmación de nueva contraseña
   * @throws PasswordChangeException si alguna validación falla
   */
  @Transactional
  public void changePassword(
      UUID usuarioId,
      String currentPassword,
      String newPassword,
      String confirmPassword) {

    // Fetch usuario
    Usuario usuario = Usuario.find("publicId", usuarioId).firstResult();
    if (usuario == null) {
      throw PasswordChangeException.usuarioNoEncontrado();
    }

    // Validar que las nuevas contraseñas coincidan
    if (!newPassword.equals(confirmPassword)) {
      log.warnf("Password mismatch for user %s", usuarioId);
      throw PasswordChangeException.passwordMismatch();
    }

    // Validar contraseña actual
    if (!passwordHasher.matches(currentPassword, usuario.getPasswordHash())) {
      log.warnf("Current password incorrect for user %s", usuarioId);
      throw PasswordChangeException.currentPasswordIncorrect();
    }

    // Validar complejidad de la nueva contraseña
    PasswordValidationResult validationResult = passwordValidation.validate(newPassword);
    if (!validationResult.isValid()) {
      log.warnf(
          "New password validation failed for user %s: missing %s",
          usuarioId, validationResult.getMissing());
      throw PasswordChangeException.passwordNotValid(validationResult.getMissing());
    }

    // Validar que la nueva no sea igual a la anterior (design decision D4)
    if (passwordHasher.matches(newPassword, usuario.getPasswordHash())) {
      log.warnf("New password same as old one for user %s", usuarioId);
      throw PasswordChangeException.passwordSameAsOld();
    }

    // Actualizar contraseña
    String newHash = passwordHasher.hash(newPassword);
    usuario.setPasswordHash(newHash);
    usuario.persist();

    // Auditar
    recordPasswordChange(usuario.getId(), usuarioId);

    log.infof("Password changed for user %s", usuarioId);
  }

  private void recordPasswordChange(Long id, UUID publicId) {
    AuditLog entry = new AuditLog();
    entry.setEntidad("usuario");
    entry.setEntidadId(id);
    entry.setAccion("change_password");
    entry.setDetalle("{\"detalle\":\"Contraseña cambiada\"}");
    entry.setRegistradoPor(id);
    entry.persist();
  }
}
