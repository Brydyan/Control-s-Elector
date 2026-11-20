package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import ec.twintailcs.electoral.auth.domain.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Servicio para operaciones de perfil de usuario.
 *
 * <p>Incluye cambio de email personal con validaciones de formato, unicidad y confirmación de
 * identidad (password).
 */
@ApplicationScoped
public class UserProfileService {

  private static final Logger log = Logger.getLogger(UserProfileService.class);

  // RFC 5322 simple regex (no es perfecta pero alcanza para validación básica)
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  private static final int MAX_EMAIL_LENGTH = 254;

  @Inject PasswordHasher passwordHasher;
  @Inject AuditLogService auditLog;

  /**
   * Actualiza el email personal del usuario.
   *
   * @param usuarioId ID del usuario
   * @param newEmailPersonal nuevo email personal (puede ser null para limpiar)
   * @param confirmPassword contraseña actual para confirmar identidad
   * @throws UserProfileException si alguna validación falla
   */
  @Transactional
  public void updatePersonalEmail(UUID usuarioId, String newEmailPersonal, String confirmPassword) {

    // Fetch usuario
    Usuario usuario = Usuario.find("publicId", usuarioId).firstResult();
    if (usuario == null) {
      throw UserProfileException.usuarioNoEncontrado();
    }

    // Validar contraseña
    if (!passwordHasher.matches(confirmPassword, usuario.getPasswordHash())) {
      log.warnf("Password incorrect for email update by user %s", usuarioId);
      throw UserProfileException.passwordIncorrect();
    }

    // Validar nuevo email personal
    String normalizedEmail =
        newEmailPersonal == null ? null : newEmailPersonal.trim().toLowerCase();

    if (normalizedEmail != null && !normalizedEmail.isEmpty()) {
      // Validar formato
      if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
        log.warnf("Invalid email format for user %s: %s", usuarioId, normalizedEmail);
        throw UserProfileException.invalidEmailFormat();
      }

      // Validar longitud
      if (normalizedEmail.length() > MAX_EMAIL_LENGTH) {
        throw UserProfileException.emailTooLong();
      }

      // Validar que no sea igual a emailControl
      if (normalizedEmail.equals(usuario.getEmail())) {
        log.warnf("Email personal same as email control for user %s", usuarioId);
        throw UserProfileException.emailSameAsControl();
      }

      // Validar unicidad: no otro usuario debe tener este email personal
      Usuario existingUser = Usuario.find("emailPersonal", normalizedEmail).firstResult();
      if (existingUser != null && !existingUser.getPublicId().equals(usuarioId)) {
        log.warnf("Email personal already in use: %s", normalizedEmail);
        throw UserProfileException.emailAlreadyInUse();
      }
    }

    // Registrar email antiguo para auditoría
    String oldEmail = usuario.getEmailPersonal();

    // Actualizar
    usuario.setEmailPersonal(normalizedEmail);
    usuario.persist();

    // Auditar
    recordEmailChange(usuario.getId(), usuarioId, oldEmail, normalizedEmail);

    log.infof("Personal email updated for user %s", usuarioId);
  }

  private void recordEmailChange(Long id, UUID publicId, String oldEmail, String newEmail) {
    String detalle =
        String.format(
            "{\"detalle\":\"Email personal actualizado\",\"oldEmail\":\"%s\",\"newEmail\":\"%s\"}",
            oldEmail == null ? "" : oldEmail, newEmail == null ? "" : newEmail);
    AuditLog entry = new AuditLog();
    entry.setEntidad("usuario");
    entry.setEntidadId(id);
    entry.setAccion("update_personal_email");
    entry.setDetalle(detalle);
    entry.setRegistradoPor(id);
    entry.persist();
  }
}
