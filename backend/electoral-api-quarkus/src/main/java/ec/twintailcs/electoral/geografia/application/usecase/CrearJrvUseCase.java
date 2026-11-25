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

/**
 * Alta de una JRV.
 *
 * <p>El numero de JRV es unico en el sistema: la JRV se identifica por su numero en el papel
 * electoral, no por su id de base.
 */
@ApplicationScoped
public class CrearJrvUseCase {

  @Inject ConfiguracionAuditService audit;
  @Inject RlsContexto rls;
  @Inject EntityManager entityManager;

  @Inject InvalidadorDeCache invalidador;

  /** Crea la JRV y deja rastro en la auditoria. */
  @Transactional
  public Jrv execute(JrvRequest dto, Long masterId, String ipAddress) {
    rls.activar(masterId);
    if (entityManager.find(Parroquia.class, dto.parroquiaId()) == null) {
      throw new GeografiaNotFoundException("No existe la parroquia " + dto.parroquiaId());
    }

    if (jrvConMismoNumero(dto.numero()) != null) {
      throw new GeografiaConflictException("Ya existe una JRV con numero " + dto.numero());
    }

    Jrv jrv = new Jrv(dto.numero(), dto.recinto(), dto.totalElectores(), dto.parroquiaId());
    jrv.persist();

    audit.registrarConIp(
        masterId, "CREAR_JRV", "jrv", jrv.getId(), "JRV creada: " + dto.numero(), ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("geografia");
    return jrv;
  }

  /** JRV con el mismo numero en el sistema, si existe. */
  private Jrv jrvConMismoNumero(String numero) {
    return entityManager
        .createQuery("select j from Jrv j where j.numero = :numero", Jrv.class)
        .setParameter("numero", numero)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }
}
