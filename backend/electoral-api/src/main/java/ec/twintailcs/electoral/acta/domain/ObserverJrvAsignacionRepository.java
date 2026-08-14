package ec.twintailcs.electoral.acta.domain;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObserverJrvAsignacionRepository extends JpaRepository<ObserverJrvAsignacion, Long> {
    boolean existsByObserverAndJrv(Usuario observer, Jrv jrv);
    List<ObserverJrvAsignacion> findByObserver(Usuario observer);
    List<ObserverJrvAsignacion> findByJrv(Jrv jrv);
}
