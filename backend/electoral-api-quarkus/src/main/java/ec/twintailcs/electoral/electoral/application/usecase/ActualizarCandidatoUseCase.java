package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Edicion de un candidato. */
@ApplicationScoped
public class ActualizarCandidatoUseCase {

  @Inject ConfiguracionAuditService audit;

  @Inject InvalidadorDeCache invalidador;

  /**
   * Actualiza el candidato. El cargo y el partido tienen que existir: si no, la operacion se
   * rechaza con 404.
   */
  @Transactional
  public Candidato execute(Long id, CandidatoRequest dto, Long masterId, String ipAddress) {
    Candidato candidato = Candidato.findById(id);
    if (candidato == null) {
      throw new ConfiguracionNotFoundException("Candidato no encontrado: " + id);
    }

    Cargo cargo = Cargo.findById(dto.cargoId());
    if (cargo == null) {
      throw new ConfiguracionNotFoundException("Cargo no encontrado: " + dto.cargoId());
    }

    Partido partido = Partido.findById(dto.partidoId());
    if (partido == null) {
      throw new ConfiguracionNotFoundException("Partido no encontrado: " + dto.partidoId());
    }

    candidato.setNombre(dto.nombre());
    candidato.setOrden(dto.orden());
    candidato.setCargo(cargo);
    candidato.setPartido(partido);

    audit.registrarConIp(
        masterId,
        "ACTUALIZAR_CANDIDATO",
        "candidato",
        id,
        "Candidato actualizado: " + dto.nombre(),
        ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("candidatos");
    return candidato;
  }
}
