package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.DeletionAudit;
import ec.twintailcs.electoral.auth.domain.DeletionStatus;
import ec.twintailcs.electoral.auth.domain.Usuario;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

/**
 * Servicio que ejecuta hard-delete permanente de cuentas marcadas para eliminación.
 *
 * <p>Implementa T-COMPLIANCE-04 D1: después de 30 días de soft-delete, el usuario y sus datos
 * personales se eliminan permanentemente. Las referencias en actas electorales se retienen por
 * requisito legal (7 años).
 *
 * <p>TODO: En producción, este método se llama via cron job diario. Por ahora es un servicio
 * invocable manualmente o via admin endpoint. Se puede integrar con quarkus-scheduler agregando la
 * dependencia en pom.xml y usando @io.quarkus.scheduler.Scheduled(cron = "0 2 * * *")
 */
@ApplicationScoped
public class HardDeleteAccountJob {

  private static final long HARD_DELETE_DAYS = 30;

  /**
   * Ejecuta hard-delete de cuentas con soft-delete vencido.
   *
   * <p>TODO: Debería ejecutarse automáticamente vía cron job (diariamente a las 2 AM UTC).
   * Actualmente se puede invocar manualmente para testing.
   */
  @Transactional
  public void hardDeleteExpiredAccounts() {
    Log.info("Starting hard-delete job for expired accounts");

    Instant cutoffDate = Instant.now().minus(java.time.Duration.ofDays(HARD_DELETE_DAYS));

    // Busca todas las cuentas que han estado en DELETED_PENDING durante >= 30 dias
    List<Usuario> usuarios =
        Usuario.find(
                "deletion_status = ?1 AND deleted_at < ?2",
                DeletionStatus.DELETED_PENDING.valor(),
                cutoffDate)
            .list();

    int deleted = 0;
    for (Usuario usuario : usuarios) {
      try {
        // Marca la auditoría antes de borrar (por si falla el DELETE y queremos saber qué pasó)
        DeletionAudit audit =
            DeletionAudit.find("usuarioId = ?1 ORDER BY deletedAt DESC", usuario.getId())
                .firstResult();
        if (audit != null) {
          audit.setHardDeletedAt(Instant.now());
          audit.persist();
        }

        // Hard-delete: elimina el usuario permanentemente
        usuario.delete();
        deleted++;

        Log.info("Hard-deleted user: " + usuario.getEmail());
      } catch (Exception e) {
        Log.error("Error hard-deleting user " + usuario.getId() + ": " + e.getMessage(), e);
      }
    }

    Log.info("Hard-delete job completed. Deleted " + deleted + " users.");
  }
}
