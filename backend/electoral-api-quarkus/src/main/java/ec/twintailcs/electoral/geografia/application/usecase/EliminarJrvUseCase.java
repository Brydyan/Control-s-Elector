package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.evidencia.domain.RlsContexto;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Map;

/**
 * Baja de una JRV.
 *
 * <p>Una JRV con actas registradas no se puede borrar: el acta es el registro electoral de esa mesa
 * y no puede quedar apuntando a una JRV que ya no existe. Se rechaza con 409 y el numero de actas
 * que bloquean el borrado.
 *
 * <p>El conteo lee {@code acta}, que tiene RLS forzada: sin activar el contexto del usuario la
 * consulta no veria las actas y el borrado pasaria sin avisar.
 */
@ApplicationScoped
public class EliminarJrvUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject RlsContexto rls;
  @Inject EntityManager entityManager;

  /** Elimina la JRV y deja rastro en la auditoria. */
  @CacheInvalidateAll(cacheName = "geografia")
  @Transactional
  public void execute(Long id, Long masterId, String ipAddress) {
    rls.activar(masterId);

    Jrv jrv = entityManager.find(Jrv.class, id);
    if (jrv == null) {
      throw new GeografiaNotFoundException("No existe la JRV " + id);
    }

    long actas =
        entityManager
            .createQuery("select count(a) from Acta a where a.jrvId = :jrvId", Long.class)
            .setParameter("jrvId", id)
            .getSingleResult();
    if (actas > 0) {
      throw new GeografiaConflictException(
          "No se puede eliminar la JRV: tiene " + actas + " actas registradas",
          Map.of(
              "actasCount",
              String.valueOf(actas),
              "hint",
              "La JRV tiene actas registradas: no se puede eliminar"));
    }

    jrv.delete();

    audit.registrarConIp(
        masterId, "ELIMINAR_JRV", "jrv", id, "JRV eliminada: " + jrv.getNumero(), ipAddress);
  }
}
