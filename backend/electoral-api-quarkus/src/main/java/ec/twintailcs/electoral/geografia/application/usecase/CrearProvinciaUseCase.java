package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.web.dto.ProvinciaRequest;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Alta de una provincia electoral.
 *
 * <p>El codigo es opcional, pero cuando viene es unico en toda la tabla (indice {@code
 * ix_provincia_codigo}): dos provincias con el mismo codigo no se distinguen para quien las usa.
 */
@ApplicationScoped
public class CrearProvinciaUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject EntityManager entityManager;

  /** Crea la provincia y deja rastro en la auditoria. */
  @CacheInvalidateAll(cacheName = "geografia")
  @Transactional
  public Provincia execute(ProvinciaRequest dto, Long masterId, String ipAddress) {
    String codigo = normalizar(dto.codigo());

    if (codigo != null && provinciaConMismoCodigo(codigo) != null) {
      throw new GeografiaConflictException("Ya existe una provincia con codigo " + codigo);
    }

    Provincia provincia = new Provincia(dto.nombre().trim(), codigo);
    provincia.persist();

    audit.registrarConIp(
        masterId,
        "CREAR_PROVINCIA",
        "provincia",
        provincia.getId(),
        "Provincia creada: " + dto.nombre(),
        ipAddress);
    return provincia;
  }

  /** Provincia con el mismo codigo, si existe. */
  private Provincia provinciaConMismoCodigo(String codigo) {
    return entityManager
        .createQuery("select p from Provincia p where p.codigo = :codigo", Provincia.class)
        .setParameter("codigo", codigo)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  /** Codigo en blanco o nulo se guardan como null: el indice unico no admite dos null. */
  static String normalizar(String codigo) {
    if (codigo == null) {
      return null;
    }
    String limpio = codigo.trim();
    return limpio.isEmpty() ? null : limpio;
  }
}
