package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Map;

/**
 * Baja de una provincia electoral.
 *
 * <p>La eliminacion en cascada esta prohibida: una provincia con cantones se rechaza con 409 y el
 * detalle de cuantos cantones bloquean el borrado.
 */
@ApplicationScoped
public class EliminarProvinciaUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject EntityManager entityManager;

  @Inject InvalidadorDeCache invalidador;

  /** Elimina la provincia y deja rastro en la auditoria. */
  @Transactional
  public void execute(Long id, Long masterId, String ipAddress) {
    Provincia provincia = entityManager.find(Provincia.class, id);
    if (provincia == null) {
      throw new GeografiaNotFoundException("No existe la provincia " + id);
    }

    long cantones =
        entityManager
            .createQuery(
                "select count(c) from Canton c where c.provinciaId = :provinciaId", Long.class)
            .setParameter("provinciaId", id)
            .getSingleResult();
    if (cantones > 0) {
      throw new GeografiaConflictException(
          "No se puede eliminar la provincia: tiene " + cantones + " cantones. Eliminelos primero",
          Map.of(
              "childrenCount",
              String.valueOf(cantones),
              "hint",
              "Elimine los cantones de esta provincia antes de eliminarla"));
    }

    provincia.delete();

    audit.registrarConIp(
        masterId,
        "ELIMINAR_PROVINCIA",
        "provincia",
        id,
        "Provincia eliminada: " + provincia.getNombre(),
        ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("geografia");
  }
}
