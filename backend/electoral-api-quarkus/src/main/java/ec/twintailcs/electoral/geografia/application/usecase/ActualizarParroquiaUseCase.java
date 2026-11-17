package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.web.dto.ParroquiaRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Edicion de una parroquia electoral. */
@ApplicationScoped
public class ActualizarParroquiaUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject EntityManager entityManager;

  /** Actualiza la parroquia y deja rastro en la auditoria. */
  @Transactional
  public Parroquia execute(Long id, ParroquiaRequest dto, Long masterId, String ipAddress) {
    Parroquia parroquia = entityManager.find(Parroquia.class, id);
    if (parroquia == null) {
      throw new GeografiaNotFoundException("No existe la parroquia " + id);
    }

    if (entityManager.find(Canton.class, dto.cantonId()) == null) {
      throw new GeografiaNotFoundException("No existe el canton " + dto.cantonId());
    }

    if (!dto.nombre().equals(parroquia.getNombre())
        && parroquiaConMismoNombreEnCanton(dto.nombre(), dto.cantonId(), id) != null) {
      throw new GeografiaConflictException(
          "Ya existe una parroquia llamada " + dto.nombre() + " en ese canton");
    }

    parroquia.setNombre(dto.nombre());
    parroquia.setEsUrbana(Boolean.TRUE.equals(dto.esUrbana()));

    audit.registrarConIp(
        masterId,
        "EDITAR_PARROQUIA",
        "parroquia",
        parroquia.getId(),
        "Parroquia editada: " + dto.nombre(),
        ipAddress);
    return parroquia;
  }

  /** Otra parroquia (distinta de la que se edita) con el mismo nombre en el mismo canton. */
  private Parroquia parroquiaConMismoNombreEnCanton(String nombre, Long cantonId, Long id) {
    return entityManager
        .createQuery(
            "select p from Parroquia p where p.nombre = :nombre and p.cantonId = :cantonId and p.id <> :id",
            Parroquia.class)
        .setParameter("nombre", nombre)
        .setParameter("cantonId", cantonId)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }
}
