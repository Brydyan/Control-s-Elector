package ec.twintailcs.electoral.notificacion.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByDestinatarioId(Long usuarioId);
    List<Notificacion> findByDestinatarioIdAndLeidaFalse(Long usuarioId);
    List<Notificacion> findByActaId(Long actaId);
    List<Notificacion> findByTipo(String tipo);
}
