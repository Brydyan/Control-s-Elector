package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.domain.AccesoNoPermitidoException;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalle;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnviarActaUseCase {

  private final ActaRepository actaRepository;
  private final ActaDetalleRepository actaDetalleRepository;
  private final AuditLogService auditLogService;

  public EnviarActaUseCase(
      ActaRepository actaRepository,
      ActaDetalleRepository actaDetalleRepository,
      AuditLogService auditLogService) {
    this.actaRepository = actaRepository;
    this.actaDetalleRepository = actaDetalleRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Acta execute(Long actaId, Long observerId, String ipAddress) {
    Acta acta =
        actaRepository
            .findByIdWithLock(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.getEstado() != EstadoActa.BORRADOR) {
      throw new ActaEstadoInvalidoException(
          "Solo BORRADOR puede pasar a ENVIADA. Estado actual: " + acta.getEstado());
    }
    if (!acta.getRegistradaPor().getId().equals(observerId)) {
      throw new AccesoNoPermitidoException("Solo el autor puede enviar su propio borrador");
    }

    validarCandidatosParaCargo(acta);
    validarSumaVotos(acta);
    acta.validarDatosObligatorios();

    acta.setEstado(EstadoActa.ENVIADA);
    Acta actaActualizada = actaRepository.save(acta);

    auditLogService.registrarConIp(
        observerId, "ACTA_ENVIADA", "acta", actaId, "Borrador oficializado", ipAddress);
    return actaActualizada;
  }

  private void validarCandidatosParaCargo(Acta acta) {
    Set<Long> ids = new HashSet<>();
    var detallesActuales = actaDetalleRepository.findByActa(acta);
    for (var d : detallesActuales) {
      if (!ids.add(d.getCandidato().getId())) {
        throw new ActaInvalidaException(
            "Candidato repetido en el detalle: " + d.getCandidato().getId());
      }
      if (!d.getCandidato().getCargo().getId().equals(acta.getCargo().getId())) {
        throw new ActaInvalidaException(
            "Candidato " + d.getCandidato().getId() + " no pertenece al cargo del acta");
      }
    }
  }

  private void validarSumaVotos(Acta acta) {
    var detalles = actaDetalleRepository.findByActa(acta);
    int sumaCandidatos = detalles.stream().mapToInt(ActaDetalle::getVotos).sum();
    int total = sumaCandidatos + acta.getVotosBlancos() + acta.getVotosNulos();
    if (total != acta.getTotalSufragantes()) {
      throw new ActaInvalidaException(
          "Los votos no cuadran: " + total + " ≠ " + acta.getTotalSufragantes());
    }
  }
}
