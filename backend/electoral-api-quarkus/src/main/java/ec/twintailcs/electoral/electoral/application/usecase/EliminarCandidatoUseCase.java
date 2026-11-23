package ec.twintailcs.electoral.electoral.application.usecase;

import ec.twintailcs.electoral.electoral.application.ConfiguracionAuditService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Baja de un candidato. */
@ApplicationScoped
public class EliminarCandidatoUseCase {

  @Inject ConfiguracionAuditService audit;

  @Inject InvalidadorDeCache invalidador;

  /** Elimina el candidato. Si no existe, la operacion se rechaza con 404. */
  @Transactional
  public void execute(Long id, Long masterId, String ipAddress) {
    Candidato candidato = Candidato.findById(id);
    if (candidato == null) {
      throw new ConfiguracionNotFoundException("Candidato no encontrado: " + id);
    }

    candidato.delete();

    audit.registrarConIp(
        masterId,
        "ELIMINAR_CANDIDATO",
        "candidato",
        id,
        "Candidato eliminado: " + candidato.getNombre(),
        ipAddress);
    // La invalidacion se aplica despues del commit (ver InvalidadorDeCache): invalidar
    // antes dejaria una ventana en la que un lector concurrente recachea el dato viejo.
    invalidador.invalidar("candidatos");
  }
}
