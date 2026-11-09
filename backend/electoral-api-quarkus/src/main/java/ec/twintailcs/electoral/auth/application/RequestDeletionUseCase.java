package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.DeletionAudit;
import ec.twintailcs.electoral.auth.domain.DeletionStatus;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.RequestDeletionResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;

/**
 * Procesa la solicitud de eliminación de cuenta (soft-delete con 30d grace period).
 *
 * <p>Implementa T-COMPLIANCE-04 D1-D4:
 *
 * <ul>
 *   <li>Valida la contraseña del usuario
 *   <li>Marca la cuenta como DELETED_PENDING (soft-delete)
 *   <li>Crea registro de auditoría
 *   <li>Envía email de confirmación con link de recuperación (7 días)
 * </ul>
 *
 * <p>El usuario puede recuperar la cuenta clickeando el link en el email dentro de 7 días. Después
 * de 30 días, el cron job realiza hard-delete permanente.
 */
@ApplicationScoped
public class RequestDeletionUseCase {

  private final PasswordHasher passwordHasher;
  private final DeletionTokenService tokenService;
  private final DeletionEmailQueue emailQueue;

  public RequestDeletionUseCase(
      PasswordHasher passwordHasher,
      DeletionTokenService tokenService,
      DeletionEmailQueue emailQueue) {
    this.passwordHasher = passwordHasher;
    this.tokenService = tokenService;
    this.emailQueue = emailQueue;
  }

  /**
   * Solicita la eliminación de una cuenta.
   *
   * @param usuarioId ID del usuario autenticado
   * @param password contraseña en claro (debe coincidir)
   * @return información del soft-delete: deadline de recuperación + deadline de hard-delete
   * @throws AuthException si la contraseña no coincide
   * @throws IllegalArgumentException si el usuario no existe
   */
  @Transactional
  public RequestDeletionResponse execute(Long usuarioId, String password) {
    Usuario usuario = Usuario.findById(usuarioId);
    if (usuario == null) {
      throw new IllegalArgumentException("Usuario no encontrado: " + usuarioId);
    }

    // Valida la contraseña: la eliminación de cuenta es una acción sensible
    if (!passwordHasher.matches(password, usuario.getPasswordHash())) {
      Log.warn("Failed deletion request: invalid password for user " + usuario.getEmail());
      throw new AuthException("Contraseña incorrecta");
    }

    // Genera token para el link de recuperación (7 días)
    String recoveryToken = tokenService.generarToken();
    Instant now = Instant.now();
    Instant recoveryDeadline = now.plus(java.time.Duration.ofDays(7));
    Instant hardDeleteDeadline = now.plus(java.time.Duration.ofDays(30));

    // Marca la cuenta como DELETED_PENDING (soft-delete)
    usuario.setDeletionStatus(DeletionStatus.DELETED_PENDING.valor());
    usuario.setDeletedAt(now);
    usuario.persist();

    // Registra en auditoría
    DeletionAudit audit = new DeletionAudit();
    audit.setUsuarioId(usuario.getId());
    audit.setEmailAtDeletion(usuario.getEmail());
    audit.setNameAtDeletion(usuario.getNombre() + " " + usuario.getApellido());
    audit.setDeletedAt(now);
    audit.setDeletionReason("user_request");
    audit.setRecoveryDeadline(recoveryDeadline);
    audit.setWasRecovered(false);
    audit.persist();

    // Envía email de confirmación (se encola para envío asincrónico)
    emailQueue.enqueueCancellationLink(
        usuario.getEmail(), usuario.getNombre(), recoveryToken, recoveryDeadline);

    Log.info("Deletion requested for user " + usuario.getEmail());

    return new RequestDeletionResponse(recoveryDeadline, hardDeleteDeadline);
  }
}
