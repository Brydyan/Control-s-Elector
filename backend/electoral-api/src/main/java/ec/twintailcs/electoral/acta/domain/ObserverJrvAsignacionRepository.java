package ec.twintailcs.electoral.acta.domain;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Deprecated 2026-08-14 — free JRV selection por observer, sin pre-asignacion.
 * Mantener por ahora para backwards-compatibility (posible uso futuro de
 * control de acceso de grano fino via RLS/permisos).
 */
@Deprecated
public interface ObserverJrvAsignacionRepository extends JpaRepository<ObserverJrvAsignacion, Long> {
    boolean existsByUsuarioAndJrv(Usuario usuario, Jrv jrv);
    List<ObserverJrvAsignacion> findByUsuario(Usuario usuario);
    List<ObserverJrvAsignacion> findByJrv(Jrv jrv);
}
