package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Map;

/**
 * Baja de un canton electoral.
 *
 * <p>La eliminacion en cascada esta prohibida por decision de diseno: la geografia es data
 * electoral critica y el MASTER tiene que ver exactamente que va a eliminar. Un canton con
 * parroquias se rechaza con 409 y el detalle de cuantos hijos bloquean el borrado.
 */
@ApplicationScoped
public class EliminarCantonUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject EntityManager entityManager;

  /** Elimina el canton y deja rastro en la auditoria. */
  @CacheInvalidateAll(cacheName = "geografia")
  @Transactional
  public void execute(Long id, Long masterId, String ipAddress) {
    Canton canton = entityManager.find(Canton.class, id);
    if (canton == null) {
      throw new GeografiaNotFoundException("No existe el canton " + id);
    }

    long parroquias =
        entityManager
            .createQuery(
                "select count(p) from Parroquia p where p.cantonId = :cantonId", Long.class)
            .setParameter("cantonId", id)
            .getSingleResult();
    if (parroquias > 0) {
      throw new GeografiaConflictException(
          "No se puede eliminar el canton: tiene " + parroquias + " parroquias. Eliminelas primero",
          Map.of(
              "childrenCount",
              String.valueOf(parroquias),
              "hint",
              "Elimine las parroquias de este canton antes de eliminarlo"));
    }

    canton.delete();

    audit.registrarConIp(
        masterId,
        "ELIMINAR_CANTON",
        "canton",
        id,
        "Canton eliminado: " + canton.getNombre(),
        ipAddress);
  }
}
