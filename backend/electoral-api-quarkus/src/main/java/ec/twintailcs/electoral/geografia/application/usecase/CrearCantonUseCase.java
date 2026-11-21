package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.web.dto.CantonRequest;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Map;

/**
 * Alta de un canton electoral.
 *
 * <p>El codigo es unico dentro de la provincia (lo decide la pantalla). El indice unico de la base
 * es global, asi que ademas se controla el choque de la base y se traduce a 409.
 */
@ApplicationScoped
public class CrearCantonUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject EntityManager entityManager;

  /** Crea el canton y deja rastro en la auditoria. */
  @CacheInvalidateAll(cacheName = "geografia")
  @Transactional
  public Canton execute(CantonRequest dto, Long masterId, String ipAddress) {
    if (entityManager.find(Provincia.class, dto.provinciaId()) == null) {
      throw new GeografiaNotFoundException("No existe la provincia " + dto.provinciaId());
    }

    if (dto.codigo() != null
        && !dto.codigo().isBlank()
        && cantonConMismoCodigoEnProvincia(dto.codigo(), dto.provinciaId()) != null) {
      throw new GeografiaConflictException(
          "Ya existe un canton con codigo " + dto.codigo() + " en esa provincia");
    }

    Canton canton = new Canton();
    canton.setNombre(dto.nombre());
    canton.setCodigo(dto.codigo());
    canton.setProvinciaId(dto.provinciaId());
    persistizar(canton, dto.codigo());

    audit.registrarConIp(
        masterId,
        "CREAR_CANTON",
        "canton",
        canton.getId(),
        "Canton creado: " + dto.nombre(),
        ipAddress);
    return canton;
  }

  /** Canton con el mismo codigo dentro de la misma provincia, si existe. */
  private Canton cantonConMismoCodigoEnProvincia(String codigo, Long provinciaId) {
    return Canton.getEntityManager()
        .createQuery(
            "select c from Canton c where c.codigo = :codigo and c.provinciaId = :provinciaId",
            Canton.class)
        .setParameter("codigo", codigo)
        .setParameter("provinciaId", provinciaId)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  /**
   * Persiste el canton traduciendo el choque del indice unico global a 409.
   *
   * <p>El indice {@code ix_canton_codigo} es unico sobre el codigo (no por provincia), asi que un
   * codigo repetido en OTRA provincia lo rechaza la base. Sin esta traduccion el error llegaria
   * como 500.
   */
  private void persistizar(Canton canton, String codigo) {
    try {
      canton.persist();
      entityManager.flush();
    } catch (jakarta.persistence.PersistenceException ex) {
      throw new GeografiaConflictException(
          "Ya existe un canton con codigo " + codigo, Map.of("campo", "codigo"));
    }
  }
}
