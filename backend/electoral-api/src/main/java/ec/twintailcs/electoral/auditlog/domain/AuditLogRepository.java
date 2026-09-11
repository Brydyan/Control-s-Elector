package ec.twintailcs.electoral.auditlog.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
  List<AuditLog> findByRegistradoPor(Long registradoPor);

  List<AuditLog> findByAccion(String accion);

  List<AuditLog> findByEntidad(String entidad);

  List<AuditLog> findByTimestampAfter(Instant timestamp);

  List<AuditLog> findByRegistradoPorOrderByTimestampDesc(Long registradoPor);

  List<AuditLog> findByEntidadAndEntidadIdOrderByTimestampDesc(String entidad, Long entidadId);
}
