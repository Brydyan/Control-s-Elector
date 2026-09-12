package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.domain.AccesoNoPermitidoException;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalle;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorregirActaUseCase {

  private final ActaRepository actaRepository;
  private final ActaDetalleRepository actaDetalleRepository;
  private final CandidatoRepository candidatoRepository;
  private final AuditLogService auditLogService;

  public CorregirActaUseCase(
      ActaRepository actaRepository,
      ActaDetalleRepository actaDetalleRepository,
      CandidatoRepository candidatoRepository,
      AuditLogService auditLogService) {
    this.actaRepository = actaRepository;
    this.actaDetalleRepository = actaDetalleRepository;
    this.candidatoRepository = candidatoRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Acta execute(
      Long actaId, ActaRequestDto dto, Long usuarioId, boolean esCoordinator, String ipAddress) {
    Acta acta =
        actaRepository
            .findByIdWithLock(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.getEstado() != EstadoActa.EN_DISPUTA) {
      throw new ActaEstadoInvalidoException(
          "Solo se pueden corregir actas EN_DISPUTA. Estado actual: " + acta.getEstado());
    }

    if (!esCoordinator && !acta.getRegistradaPor().getId().equals(usuarioId)) {
      throw new AccesoNoPermitidoException("Solo puedes corregir tus propias actas");
    }

    validarCandidatosParaCargoDto(dto, acta.getCargo());
    validarSumaVotosDto(dto);

    acta.setTotalSufragantes(dto.getTotalSufragantes());
    acta.setVotosBlancos(dto.getVotosBlancos());
    acta.setVotosNulos(dto.getVotosNulos());
    acta.setEstado(EstadoActa.CORREGIDA);

    actaDetalleRepository.deleteByActa(acta);
    actaDetalleRepository.flush();
    persistirDetalles(acta, dto.getDetalles());

    Acta actaActualizada = actaRepository.save(acta);

    auditLogService.registrarConIp(
        usuarioId,
        "ACTA_CORREGIDA",
        "acta",
        actaId,
        "Acta corregida por usuario " + usuarioId,
        ipAddress);
    return actaActualizada;
  }

  private void validarCandidatosParaCargoDto(ActaRequestDto dto, Cargo cargo) {
    Set<Long> ids = new HashSet<>();
    for (ActaDetalleDto detalle : dto.getDetalles()) {
      if (!ids.add(detalle.getCandidatoId())) {
        throw new ActaInvalidaException(
            "Candidato repetido en el detalle: " + detalle.getCandidatoId());
      }
      Candidato candidato =
          candidatoRepository
              .findById(detalle.getCandidatoId())
              .orElseThrow(
                  () ->
                      new ActaInvalidaException(
                          "Candidato no encontrado: " + detalle.getCandidatoId()));
      if (!candidato.getCargo().getId().equals(cargo.getId())) {
        throw new ActaInvalidaException(
            "Candidato "
                + candidato.getId()
                + " pertenece al cargo "
                + candidato.getCargo().getId()
                + " pero el acta es del cargo "
                + cargo.getId());
      }
    }
  }

  private void validarSumaVotosDto(ActaRequestDto dto) {
    int sumaCandidatos = dto.getDetalles().stream().mapToInt(ActaDetalleDto::getVotos).sum();
    int total = sumaCandidatos + dto.getVotosBlancos() + dto.getVotosNulos();
    if (total != dto.getTotalSufragantes()) {
      throw new ActaInvalidaException(
          "Los votos no cuadran. Candidatos="
              + sumaCandidatos
              + " + Blancos="
              + dto.getVotosBlancos()
              + " + Nulos="
              + dto.getVotosNulos()
              + " = "
              + total
              + " ≠ "
              + dto.getTotalSufragantes()
              + " (total sufragantes)");
    }
  }

  private void persistirDetalles(Acta acta, List<ActaDetalleDto> detalles) {
    for (ActaDetalleDto detalleDto : detalles) {
      Candidato candidato =
          candidatoRepository
              .findById(detalleDto.getCandidatoId())
              .orElseThrow(
                  () ->
                      new ActaInvalidaException(
                          "Candidato no encontrado: " + detalleDto.getCandidatoId()));
      ActaDetalle detalle = new ActaDetalle(acta, candidato, detalleDto.getVotos());
      actaDetalleRepository.save(detalle);
    }
  }
}
