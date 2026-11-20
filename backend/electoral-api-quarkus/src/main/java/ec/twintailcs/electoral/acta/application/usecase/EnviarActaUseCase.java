package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.application.ActaAuditService;
import ec.twintailcs.electoral.acta.application.ActaNotificacionService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.auth.domain.AccesoNoPermitidoException;
import ec.twintailcs.electoral.evidencia.domain.RlsContexto;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.Recinto;
import ec.twintailcs.electoral.geografia.domain.RecintoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Transicion BORRADOR → ENVIADA: oficializa el borrador con la validacion completa de integridad
 * electoral.
 */
@ApplicationScoped
public class EnviarActaUseCase {

  @Inject ActaRepository repository;
  @Inject ActaAuditService audit;
  @Inject ActaNotificacionService notificacion;
  @Inject RecintoRepository recintoRepository;
  @Inject RlsContexto rls;

  /**
   * Envia el borrador.
   *
   * @param actaId id interno del acta
   * @param observerId id interno del observer autor
   * @param ipAddress IP del cliente
   */
  @Transactional
  public Acta execute(Long actaId, Long observerId, String ipAddress) {
    Acta acta =
        repository
            .buscarPorIdConLock(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.getEstado() != EstadoActa.BORRADOR) {
      throw new ActaEstadoInvalidoException(
          "Solo BORRADOR puede pasar a ENVIADA. Estado actual: " + acta.getEstado());
    }
    if (!acta.getRegistradaPorId().equals(observerId)) {
      throw new AccesoNoPermitidoException("Solo el autor puede enviar su propio borrador");
    }

    validarCandidatosParaCargo(acta);
    validarSumaVotos(acta);
    acta.validarDatosObligatorios();

    acta.setEstado(EstadoActa.ENVIADA);

    // Notify coordinator of the sent acta
    notificarCoordinator(acta, observerId);

    audit.registrarConIp(
        observerId, "ACTA_ENVIADA", "acta", actaId, "Borrador oficializado", ipAddress);
    return acta;
  }

  /** Ningun candidato puede repetirse y todos tienen que pertenecer al cargo del acta. */
  private void validarCandidatosParaCargo(Acta acta) {
    Set<Long> ids = new HashSet<>();
    for (ActaRepository.FilaDetalle detalle : repository.detallesDe(acta.getId())) {
      if (!ids.add(detalle.candidatoId())) {
        throw new ActaInvalidaException(
            "Candidato repetido en el detalle: " + detalle.candidatoId());
      }
      if (!detalle.cargoId().equals(acta.getCargoId())) {
        throw new ActaInvalidaException(
            "Candidato " + detalle.candidatoId() + " no pertenece al cargo del acta");
      }
    }
  }

  /** La suma de votos tiene que cerrar con el total de sufragantes. */
  private void validarSumaVotos(Acta acta) {
    List<ActaRepository.FilaDetalle> detalles = repository.detallesDe(acta.getId());
    int sumaCandidatos = detalles.stream().mapToInt(ActaRepository.FilaDetalle::votos).sum();
    int total = sumaCandidatos + acta.getVotosBlancos() + acta.getVotosNulos();
    if (total != acta.getTotalSufragantes()) {
      throw new ActaInvalidaException(
          "Los votos no cuadran: " + total + " ≠ " + acta.getTotalSufragantes());
    }
  }

  /** Notifies the coordinator of the recinto that an acta was sent. */
  private void notificarCoordinator(Acta acta, Long observerId) {
    // Get JRV to find associated Recinto
    Jrv jrv = Jrv.findById(acta.getJrvId());
    if (jrv == null || jrv.getRecintoId() == null) {
      return; // No recinto assigned yet, skip notification
    }

    // Get Recinto and its Coordinator
    Recinto recinto = recintoRepository.findByIdOptional(jrv.getRecintoId()).orElse(null);
    if (recinto == null || recinto.getCoordinatorId() == null) {
      return; // No coordinator assigned, skip notification
    }

    // Notify coordinator
    rls.activar(observerId);
    notificacion.notificar(
        recinto.getCoordinatorId(),
        "ACTA_ENVIADA",
        acta.getId(),
        "Se ha enviado un acta para revisión de la JRV " + jrv.getNumero());
  }
}
