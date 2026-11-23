package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.web.dto.CantonRequest;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Map;

/**
 * Edicion de un canton electoral.
 *
 * <p>El canton no cambia de provincia: moverlo de provincia es otra operacion (y la pantalla no la
 * ofrece). El codigo sigue siendo unico dentro de la provincia.
 */
@ApplicationScoped
public class ActualizarCantonUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject EntityManager entityManager;

  @Inject InvalidadorDeCache invalidador;

  /** Actualiza el canton y deja rastro en la auditoria. */
  @Transactional
  public Canton execute(Long id, CantonRequest dto, Long masterId, String ipAddress) {
    Canton canton = entityManager.find(Canton.class, id);
    if (canton == null) {
      throw new GeografiaNotFoundException("No existe el canton " + id);
    }

    if (entityManager.find(Provincia.class, dto.provinciaId()) == null) {
      throw new GeografiaNotFoundException("No existe la provincia " + dto.provinciaId());
    }

    if (dto.codigo() != null
        && !dto.codigo().isBlank()
        && !dto.codigo().equals(canton.getCodigo())
        && cantonConMismoCodigoEnProvincia(dto.codigo(), dto.provinciaId(), id) != null) {
      throw new GeografiaConflictException(
          "Ya existe un canton con codigo " + dto.codigo() + " en esa provincia");
    }

    canton.setNombre(dto.nombre());
    canton.setCodigo(dto.codigo());
    try {
      entityManager.flush();
    } catch (jakarta.persistence.PersistenceException ex) {
      throw new GeografiaConflictException(
          "Ya existe un canton con codigo " + dto.codigo(), Map.of("campo", "codigo"));
    }

    audit.registrarConIp(
        masterId,
        "EDITAR_CANTON",
        "canton",
        canton.getId(),
        "Canton editado: " + dto.nombre(),
        ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("geografia");
    return canton;
  }

  /** Otro canton (distinto del que se esta editando) con el mismo codigo en la misma provincia. */
  private Canton cantonConMismoCodigoEnProvincia(String codigo, Long provinciaId, Long id) {
    return Canton.getEntityManager()
        .createQuery(
            "select c from Canton c where c.codigo = :codigo and c.provinciaId = :provinciaId and c.id <> :id",
            Canton.class)
        .setParameter("codigo", codigo)
        .setParameter("provinciaId", provinciaId)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }
}
