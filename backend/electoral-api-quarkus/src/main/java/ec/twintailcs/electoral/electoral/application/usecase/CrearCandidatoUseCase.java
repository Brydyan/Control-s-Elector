package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Alta de un candidato. */
@ApplicationScoped
public class CrearCandidatoUseCase {

  @Inject ConfiguracionAuditService audit;

  /**
   * Crea el candidato. El cargo y el partido tienen que existir: si no, la operacion se rechaza con
   * 404.
   */
  @CacheInvalidateAll(cacheName = "candidatos")
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
    return candidato;
  }
}
