package ec.twintailcs.electoral.evidencia.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenciaRepository extends JpaRepository<Evidencia, Long> {

  /** Buscar por publicId (UUIDv7). */
  Optional<Evidencia> findByPublicId(UUID publicId);

  /** Todas las evidencias de un acta (JRV + cargo). */
  List<Evidencia> findByJrvIdAndCargoId(Long jrvId, Long cargoId);

  /** Evidencias del OBSERVER (su propio historial). */
  List<Evidencia> findByUsuarioId(Long usuarioId);

  /** Bandeja de aprobacion: todo lo pendiente de revision. */
  List<Evidencia> findByEstado(EstadoEvidencia estado);
}
