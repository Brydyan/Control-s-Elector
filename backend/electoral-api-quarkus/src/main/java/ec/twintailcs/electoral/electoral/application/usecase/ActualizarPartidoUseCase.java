package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionConflictException;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Edicion de un partido politico. */
@ApplicationScoped
public class ActualizarPartidoUseCase {

  @Inject ConfiguracionAuditService audit;

  @Inject InvalidadorDeCache invalidador;

  /**
   * Actualiza el partido. El numero de lista sigue siendo unico: si el numero pedido ya lo tiene
   * otro partido, la operacion se rechaza con 409.
   */
  @Transactional
  public Partido execute(Long id, PartidoRequest dto, Long masterId, String ipAddress) {
    Partido partido = Partido.findById(id);
    if (partido == null) {
      throw new ConfiguracionNotFoundException("Partido no encontrado: " + id);
    }

    Partido conMismoNumero = Partido.find("numeroLista", dto.numeroLista()).firstResult();
    if (conMismoNumero != null && !conMismoNumero.getId().equals(id)) {
      throw new ConfiguracionConflictException(
          "Ya existe un partido con numeroLista " + dto.numeroLista());
    }

    partido.setNombre(dto.nombre());
    partido.setSiglas(dto.siglas());
    partido.setNumeroLista(dto.numeroLista());

    audit.registrarConIp(
        masterId,
        "ACTUALIZAR_PARTIDO",
        "partido",
        id,
        "Partido actualizado: " + dto.siglas(),
        ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("partidos");
    return partido;
  }
}
