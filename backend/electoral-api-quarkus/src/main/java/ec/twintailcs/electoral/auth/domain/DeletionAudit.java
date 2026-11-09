package ec.twintailcs.electoral.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Auditoría de solicitudes y cumplimiento de eliminaciones de cuenta (T-COMPLIANCE-04).
 *
 * <p>Registra cada solicitud de eliminación, recuperación, y hard-delete permanente. Mantiene
 * snapshot de datos del usuario en el momento de solicitud (email, nombre) para auditoria y
 * cumplimiento legal.
 */
@Entity
@Table(name = "deletion_audit")
public class DeletionAudit extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  /**
   * ID del usuario que solicito la eliminacion.
   *
   * <p>No es una FK: el usuario se puede eliminar permanentemente (hard-delete) mientras la
   * auditoria sigue existiendo. Este campo puede quedar "huérfano" post hard-delete.
   */
  @Column(name = "usuario_id", nullable = false)
  Long usuarioId;

  /** Snapshot del email del usuario en el momento de la solicitud de eliminacion. */
  @Column(name = "email_at_deletion", nullable = false, length = 254)
  String emailAtDeletion;

  /** Snapshot del nombre completo (nombre + apellido) en el momento de la solicitud. */
  @Column(name = "name_at_deletion", length = 200)
  String nameAtDeletion;

  /**
   * Momento cuando se solicito la eliminacion (T+0).
   *
   * <p>Se usa para calcular si pasaron 30 dias (hard-delete) y para recuperar el snapshot de
   * auditoria.
   */
  @Column(name = "deleted_at", nullable = false)
  Instant deletedAt;

  /** Razon de la eliminacion (ej: "user_request"). */
  @Column(name = "deletion_reason", nullable = false, length = 64)
  String deletionReason;

  /**
   * Fecha límite para recuperacion (T+7d).
   *
   * <p>Despues de esta fecha, el usuario no puede recuperar su cuenta clickeando el link en
   * email.
   */
  @Column(name = "recovery_deadline", nullable = false)
  Instant recoveryDeadline;

  /**
   * {@code true} si el usuario cancelo la eliminacion antes del deadline de recuperacion.
   *
   * <p>Default: false.
   */
  @Column(name = "was_recovered", nullable = false)
  boolean wasRecovered = false;

  /**
   * Momento cuando se cancelo la eliminacion.
   *
   * <p>NULL si no fue cancelada.
   */
  @Column(name = "recovered_at")
  Instant recoveredAt;

  /**
   * Momento cuando se completo el hard-delete permanente (T+30d).
   *
   * <p>NULL si aun no fue hard-deleted. Una vez completado, el usuario y sus datos personales
   * estan eliminados permanentemente de la base (excepto referencias en actas electorales).
   */
  @Column(name = "hard_deleted_at")
  Instant hardDeletedAt;

  /** Momento cuando se creo este registro de auditoria. */
  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public Long getUsuarioId() {
    return usuarioId;
  }

  public void setUsuarioId(Long usuarioId) {
    this.usuarioId = usuarioId;
  }

  public String getEmailAtDeletion() {
    return emailAtDeletion;
  }

  public void setEmailAtDeletion(String emailAtDeletion) {
    this.emailAtDeletion = emailAtDeletion;
  }

  public String getNameAtDeletion() {
    return nameAtDeletion;
  }

  public void setNameAtDeletion(String nameAtDeletion) {
    this.nameAtDeletion = nameAtDeletion;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public String getDeletionReason() {
    return deletionReason;
  }

  public void setDeletionReason(String deletionReason) {
    this.deletionReason = deletionReason;
  }

  public Instant getRecoveryDeadline() {
    return recoveryDeadline;
  }

  public void setRecoveryDeadline(Instant recoveryDeadline) {
    this.recoveryDeadline = recoveryDeadline;
  }

  public boolean wasRecovered() {
    return wasRecovered;
  }

  public void setWasRecovered(boolean wasRecovered) {
    this.wasRecovered = wasRecovered;
  }

  public Instant getRecoveredAt() {
    return recoveredAt;
  }

  public void setRecoveredAt(Instant recoveredAt) {
    this.recoveredAt = recoveredAt;
  }

  public Instant getHardDeletedAt() {
    return hardDeletedAt;
  }

  public void setHardDeletedAt(Instant hardDeletedAt) {
    this.hardDeletedAt = hardDeletedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
