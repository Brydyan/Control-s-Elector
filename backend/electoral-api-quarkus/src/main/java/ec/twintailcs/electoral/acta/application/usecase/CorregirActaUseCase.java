package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.application.ActaAuditService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.dto.ActaRequestDto;
import ec.twintailcs.electoral.auth.domain.AccesoNoPermitidoException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Corrige un acta en estado EN_DISPUTA y la deja en CORREGIDA.
 *
 * <p>La pueden pedir el OBSERVER autor de su acta o el COORDINATOR (que corrige sobre cualquier
 * JRV). El borrado de las lineas va antes del insert de las nuevas: el UNIQUE (acta_id,
 * candidato_id) no admite las dos cosas a la vez.
 */
@ApplicationScoped
public class CorregirActaUseCase {

  @Inject ActaRepository repository;
  @Inject ActaAuditService audit;
  @Inject ValidacionActa validacion;

  /**
   * Corrige el acta.
   *
   * @param actaId id interno del acta
   * @param dto datos corregidos del acta
   * @param usuarioId id interno del usuario que corrige
   * @param esCoordinator {@code true} si quien corrige es COORDINATOR/MASTER
   * @param ipAddress IP del cliente
   */
  @Transactional
  public Acta execute(
      Long actaId, ActaRequestDto dto, Long usuarioId, boolean esCoordinator, String ipAddress) {
    Acta acta =
        repository
            .buscarPorIdConLock(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.getEstado() != EstadoActa.EN_DISPUTA) {
      throw new ActaEstadoInvalidoException(
          "Solo se pueden corregir actas EN_DISPUTA. Estado actual: " + acta.getEstado());
    }

    if (!esCoordinator && !acta.getRegistradaPorId().equals(usuarioId)) {
      throw new AccesoNoPermitidoException("Solo puedes corregir tus propias actas");
    }

    validacion.validarCandidatosParaCargo(dto.detalles(), acta.getCargoId());
    validacion.validarSumaVotos(
        dto.detalles(), dto.votosBlancos(), dto.votosNulos(), dto.totalSufragantes());

    acta.setTotalSufragantes(dto.totalSufragantes());
    acta.setVotosBlancos(dto.votosBlancos());
    acta.setVotosNulos(dto.votosNulos());
    acta.setEstado(EstadoActa.CORREGIDA);

    // El flush entre el delete y el insert es obligatorio: Hibernate ejecuta los INSERT de
    // ActaDetalle antes que los DELETE en el mismo flush y el UNIQUE (acta_id, candidato_id)
    // revienta con duplicate key.
    repository.borrarDetalles(acta.getId());
    Acta.getEntityManager().flush();

    persistirDetalles(acta.getId(), dto.detalles());

    audit.registrarConIp(
        usuarioId,
        "ACTA_CORREGIDA",
        "acta",
        actaId,
        "Acta corregida por usuario " + usuarioId,
        ipAddress);
    return acta;
  }

  /** Persiste las lineas del acta. */
  private void persistirDetalles(Long actaId, List<ActaDetalleDto> detalles) {
    for (ActaDetalleDto detalleDto : detalles) {
      repository.persistirDetalle(actaId, detalleDto.candidatoId(), detalleDto.votos());
    }
  }
}
