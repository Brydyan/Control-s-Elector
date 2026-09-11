package ec.twintailcs.electoral.notificacion.domain;

import ec.twintailcs.electoral.auth.domain.Usuario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByDestinatarioId(Long usuarioId);
    List<Notificacion> findByDestinatarioIdAndLeidaFalse(Long usuarioId);
    List<Notificacion> findByActaId(Long actaId);
    List<Notificacion> findByTipo(String tipo);

    List<Notificacion> findByDestinatarioIdAndLeidaFalseOrderByCreadoEnDesc(Long destinatarioId);

    long countByDestinatarioIdAndLeidaFalse(Long destinatarioId);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.destinatarioId = :destinatarioId")
    void marcarTodasLeidas(@Param("destinatarioId") Long destinatarioId);
}
