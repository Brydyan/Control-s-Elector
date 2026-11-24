package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionConflictException;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Alta de un partido politico. */
@ApplicationScoped
public class CrearPartidoUseCase {

  @Inject ConfiguracionAuditService audit;

  @Inject InvalidadorDeCache invalidador;

  /**
   * Crea el partido. El numero de lista es unico: si ya existe uno con el mismo numero, la
   * operacion se rechaza con 409.
   */
  @Transactional
  public Partido execute(PartidoRequest dto, Long masterId, String ipAddress) {
    if (Partido.find("numeroLista", dto.numeroLista()).firstResult() != null) {
      throw new ConfiguracionConflictException(
          "Ya existe un partido con numeroLista " + dto.numeroLista());
    }

    Partido partido = new Partido(dto.nombre(), dto.siglas(), dto.numeroLista());
    partido.persist();

    audit.registrarConIp(
        masterId,
        "CREAR_PARTIDO",
        "partido",
        partido.getId(),
        "Partido creado: " + dto.siglas(),
        ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("partidos");
    return partido;
  }
}
