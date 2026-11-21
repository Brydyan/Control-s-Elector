package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.web.dto.JrvRequest;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Edicion de una JRV. */
@ApplicationScoped
public class ActualizarJrvUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject EntityManager entityManager;

  /** Actualiza la JRV y deja rastro en la auditoria. */
  @CacheInvalidateAll(cacheName = "geografia")
  @Transactional
  public Jrv execute(Long id, JrvRequest dto, Long masterId, String ipAddress) {
    Jrv jrv = entityManager.find(Jrv.class, id);
    if (jrv == null) {
      throw new GeografiaNotFoundException("No existe la JRV " + id);
    }

    if (entityManager.find(Parroquia.class, dto.parroquiaId()) == null) {
      throw new GeografiaNotFoundException("No existe la parroquia " + dto.parroquiaId());
    }

    if (!dto.numero().equals(jrv.getNumero()) && jrvConMismoNumero(dto.numero(), id) != null) {
      throw new GeografiaConflictException("Ya existe una JRV con numero " + dto.numero());
    }

    jrv.setNumero(dto.numero());
    jrv.setRecinto(dto.recinto());
    jrv.setTotalElectores(dto.totalElectores());

    audit.registrarConIp(
        masterId, "EDITAR_JRV", "jrv", jrv.getId(), "JRV editada: " + dto.numero(), ipAddress);
    return jrv;
  }

  /** Otra JRV (distinta de la que se edita) con el mismo numero. */
  private Jrv jrvConMismoNumero(String numero, Long id) {
    return entityManager
        .createQuery("select j from Jrv j where j.numero = :numero and j.id <> :id", Jrv.class)
        .setParameter("numero", numero)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }
}
