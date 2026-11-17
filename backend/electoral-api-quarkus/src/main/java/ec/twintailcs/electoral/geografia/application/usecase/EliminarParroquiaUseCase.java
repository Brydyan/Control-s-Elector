package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Map;

/**
 * Baja de una parroquia electoral.
 *
 * <p>La eliminacion en cascada esta prohibida: una parroquia con JRVs se rechaza con 409 y el
 * detalle de cuantos JRVs bloquean el borrado.
 */
@ApplicationScoped
public class EliminarParroquiaUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject EntityManager entityManager;

  /** Elimina la parroquia y deja rastro en la auditoria. */
  @Transactional
  public void execute(Long id, Long masterId, String ipAddress) {
    Parroquia parroquia = entityManager.find(Parroquia.class, id);
    if (parroquia == null) {
      throw new GeografiaNotFoundException("No existe la parroquia " + id);
    }

    long jrvs =
        entityManager
            .createQuery(
                "select count(j) from Jrv j where j.parroquiaId = :parroquiaId", Long.class)
            .setParameter("parroquiaId", id)
            .getSingleResult();
    if (jrvs > 0) {
      throw new GeografiaConflictException(
          "No se puede eliminar la parroquia: tiene " + jrvs + " JRVs. Eliminelas primero",
          Map.of(
              "childrenCount",
              String.valueOf(jrvs),
              "hint",
              "Elimine las JRVs de esta parroquia antes de eliminarla"));
    }

    parroquia.delete();

    audit.registrarConIp(
        masterId,
        "ELIMINAR_PARROQUIA",
        "parroquia",
        id,
        "Parroquia eliminada: " + parroquia.getNombre(),
        ipAddress);
  }
}
