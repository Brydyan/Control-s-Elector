package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.web.dto.ParroquiaRequest;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Alta de una parroquia electoral.
 *
 * <p>El nombre tiene que ser unico dentro del canton: dos parroquias con el mismo nombre en el
 * mismo canton no se distinguen para quien las usa.
 */
@ApplicationScoped
public class CrearParroquiaUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject EntityManager entityManager;

  @Inject InvalidadorDeCache invalidador;

  /** Crea la parroquia y deja rastro en la auditoria. */
  @Transactional
  public Parroquia execute(ParroquiaRequest dto, Long masterId, String ipAddress) {
    if (entityManager.find(Canton.class, dto.cantonId()) == null) {
      throw new GeografiaNotFoundException("No existe el canton " + dto.cantonId());
    }

    if (parroquiaConMismoNombreEnCanton(dto.nombre(), dto.cantonId()) != null) {
      throw new GeografiaConflictException(
          "Ya existe una parroquia llamada " + dto.nombre() + " en ese canton");
    }

    Parroquia parroquia =
        new Parroquia(dto.nombre(), Boolean.TRUE.equals(dto.esUrbana()), dto.cantonId());
    parroquia.persist();

    audit.registrarConIp(
        masterId,
        "CREAR_PARROQUIA",
        "parroquia",
        parroquia.getId(),
        "Parroquia creada: " + dto.nombre(),
        ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("geografia");
    return parroquia;
  }

  /** Parroquia con el mismo nombre en el mismo canton, si existe. */
  private Parroquia parroquiaConMismoNombreEnCanton(String nombre, Long cantonId) {
    return entityManager
        .createQuery(
            "select p from Parroquia p where p.nombre = :nombre and p.cantonId = :cantonId",
            Parroquia.class)
        .setParameter("nombre", nombre)
        .setParameter("cantonId", cantonId)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }
}
