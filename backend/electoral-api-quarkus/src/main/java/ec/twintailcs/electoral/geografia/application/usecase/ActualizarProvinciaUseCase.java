package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.web.dto.ProvinciaRequest;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Edicion de una provincia electoral. */
@ApplicationScoped
public class ActualizarProvinciaUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject EntityManager entityManager;

  /** Actualiza la provincia y deja rastro en la auditoria. */
  @CacheInvalidateAll(cacheName = "geografia")
  @Transactional
  public Provincia execute(Long id, ProvinciaRequest dto, Long masterId, String ipAddress) {
    Provincia provincia = entityManager.find(Provincia.class, id);
    if (provincia == null) {
      throw new GeografiaNotFoundException("No existe la provincia " + id);
    }

    String codigo = CrearProvinciaUseCase.normalizar(dto.codigo());
    if (codigo != null
        && !codigo.equals(provincia.getCodigo())
        && provinciaConMismoCodigo(codigo, id) != null) {
      throw new GeografiaConflictException("Ya existe una provincia con codigo " + codigo);
    }

    provincia.setNombre(dto.nombre().trim());
    provincia.setCodigo(codigo);

    audit.registrarConIp(
        masterId,
        "EDITAR_PROVINCIA",
        "provincia",
        provincia.getId(),
        "Provincia editada: " + dto.nombre(),
        ipAddress);
    return provincia;
  }

  /** Otra provincia (distinta de la que se edita) con el mismo codigo. */
  private Provincia provinciaConMismoCodigo(String codigo, Long id) {
    return entityManager
        .createQuery(
            "select p from Provincia p where p.codigo = :codigo and p.id <> :id", Provincia.class)
        .setParameter("codigo", codigo)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }
}
