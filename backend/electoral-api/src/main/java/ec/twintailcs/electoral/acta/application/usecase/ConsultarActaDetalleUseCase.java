package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalle;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleCompletoDto;
import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import ec.twintailcs.electoral.auditlog.domain.AuditLogRepository;
import ec.twintailcs.electoral.evidencia.application.EvidenciaService;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-BE-21 / A.2: ensambla el {@link ActaDetalleCompletoDto} con detalles, evidencia y motivo de
 * disputa.
 *
 * <p>El listado plano (sin colecciones) sigue en otro método del controller — este use case
 * es para la vista de detalle donde SÍ se cargan colecciones.
 */
@Service
public class ConsultarActaDetalleUseCase {

  private final ActaRepository actaRepository;
  private final ActaDetalleRepository actaDetalleRepository;
  private final EvidenciaRepository evidenciaRepository;
  private final EvidenciaService evidenciaService;
  private final AuditLogRepository auditLogRepository;

  public ConsultarActaDetalleUseCase(
      ActaRepository actaRepository,
      ActaDetalleRepository actaDetalleRepository,
      EvidenciaRepository evidenciaRepository,
      EvidenciaService evidenciaService,
      AuditLogRepository auditLogRepository) {
    this.actaRepository = actaRepository;
    this.actaDetalleRepository = actaDetalleRepository;
    this.evidenciaRepository = evidenciaRepository;
    this.evidenciaService = evidenciaService;
    this.auditLogRepository = auditLogRepository;
  }

  @Transactional(readOnly = true)
  public ActaDetalleCompletoDto ejecutar(Long actaId) {
    Acta acta =
        actaRepository
            .findById(actaId)
            .orElseThrow(() -> new EntityNotFoundException("Acta no encontrada: " + actaId));

    List<ActaDetalleDto> detalles =
        actaDetalleRepository.findByActaId(actaId).stream()
            .map(this::toDetalleDto)
            .toList();

    Evidencia evidencia = primeraEvidencia(actaId);
    UUID evidenciaPublicId = evidencia == null ? null : evidencia.getPublicId();
    String urlEvidencia = null;
    if (evidencia != null) {
      try {
        urlEvidencia = evidenciaService.presignDownload(evidencia.getPublicId());
      } catch (Exception ignored) {
        // No propagamos: el DTO se entrega con urlEvidencia=null si S3 falla.
      }
    }

    String motivoDisputa =
        (acta.getEstado() == EstadoActa.EN_DISPUTA)
            ? buscarUltimoMotivoDisputa(actaId)
            : null;

    return new ActaDetalleCompletoDto(
        acta.getId(),
        acta.getJrv().getId(),
        acta.getCargo().getId(),
        acta.getRegistradaPor().getId(),
        acta.getEstado(),
        acta.getTotalSufragantes(),
        acta.getVotosBlancos(),
        acta.getVotosNulos(),
        acta.getFechaRegistro(),
        acta.getMotivoAnulacion(),
        acta.getCargo().getNombre(),
        acta.getJrv().getNumero(),
        sumaVotosCandidatos(detalles),
        detalles,
        evidenciaPublicId,
        urlEvidencia,
        motivoDisputa);
  }

  private Evidencia primeraEvidencia(Long actaId) {
    // EvidenciaRepository no tiene findByActaId ordenado. Carga simple y filtro en memoria.
    return evidenciaRepository.findAll().stream()
        .filter(e -> e.getActa() != null && e.getActa().getId().equals(actaId))
        .findFirst()
        .orElse(null);
  }

  private String buscarUltimoMotivoDisputa(Long actaId) {
    return auditLogRepository.findAll().stream()
        .filter(a -> "acta".equals(a.getEntidad()) && a.getEntidadId().equals(actaId))
        .filter(a -> a.getAccion() != null && a.getAccion().contains("DISPUTA"))
        .max((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
        .map(a -> a.getDetalle() == null ? null : a.getDetalle().toString())
        .orElse(null);
  }

  private Long sumaVotosCandidatos(List<ActaDetalleDto> detalles) {
    return detalles.stream().mapToLong(d -> d.getVotos() == null ? 0L : d.getVotos()).sum();
  }

  private ActaDetalleDto toDetalleDto(ActaDetalle d) {
    return new ActaDetalleDto(d.getCandidato().getId(), d.getVotos());
  }
}
