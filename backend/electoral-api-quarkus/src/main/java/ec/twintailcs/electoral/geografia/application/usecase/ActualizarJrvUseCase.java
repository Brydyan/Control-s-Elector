package ec.twintailcs.electoral.geografia.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.evidencia.domain.RlsContexto;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.web.dto.JrvRequest;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Edicion de una JRV. */
@ApplicationScoped
public class ActualizarJrvUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject RlsContexto rls;
  @Inject EntityManager entityManager;

  @Inject InvalidadorDeCache invalidador;

  /** Actualiza la JRV y deja rastro en la auditoria. */
  @Transactional
  public Jrv execute(Long id, JrvRequest dto, Long masterId, String ipAddress) {
    rls.activar(masterId);
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
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("geografia");
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
