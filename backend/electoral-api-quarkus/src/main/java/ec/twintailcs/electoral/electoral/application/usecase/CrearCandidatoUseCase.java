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

/** Alta de un candidato. */
@ApplicationScoped
public class CrearCandidatoUseCase {

  @Inject ConfiguracionAuditService audit;

  @Inject InvalidadorDeCache invalidador;

  /**
   * Crea el candidato. El cargo y el partido tienen que existir: si no, la operacion se rechaza con
   * 404.
   */
  @Transactional
  public Candidato execute(CandidatoRequest dto, Long masterId, String ipAddress) {
    Cargo cargo = Cargo.findById(dto.cargoId());
    if (cargo == null) {
      throw new ConfiguracionNotFoundException("Cargo no encontrado: " + dto.cargoId());
    }

    Partido partido = Partido.findById(dto.partidoId());
    if (partido == null) {
      throw new ConfiguracionNotFoundException("Partido no encontrado: " + dto.partidoId());
    }

    Candidato candidato = new Candidato(dto.nombre(), dto.orden(), cargo, partido);
    candidato.persist();

    audit.registrarConIp(
        masterId,
        "CREAR_CANDIDATO",
        "candidato",
        candidato.getId(),
        "Candidato: " + dto.nombre() + " para cargo " + cargo.getNombre(),
        ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("candidatos");
    return candidato;
  }
}
