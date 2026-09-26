package ec.twintailcs.electoral.evidencia.application;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.evidencia.application.usecase.CambiarEstadoEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.ConsultarEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.EliminarEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.MarcarObservacionesUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.SubirEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fachada para compatibilidad y operaciones del módulo de evidencia. Delega la lógica en casos de
 * uso de aplicación.
 */
@Service
public class EvidenciaService {

  private final SubirEvidenciaUseCase subirEvidenciaUseCase;
  private final ConsultarEvidenciaUseCase consultarEvidenciaUseCase;
  private final CambiarEstadoEvidenciaUseCase cambiarEstadoEvidenciaUseCase;
  private final EliminarEvidenciaUseCase eliminarEvidenciaUseCase;
  private final MarcarObservacionesUseCase marcarObservacionesUseCase;
  private final AuditLogService auditLogService;
  private final UsuarioRepository usuarioRepository;

  public EvidenciaService(
      SubirEvidenciaUseCase subirEvidenciaUseCase,
      ConsultarEvidenciaUseCase consultarEvidenciaUseCase,
      CambiarEstadoEvidenciaUseCase cambiarEstadoEvidenciaUseCase,
      EliminarEvidenciaUseCase eliminarEvidenciaUseCase,
      MarcarObservacionesUseCase marcarObservacionesUseCase,
      AuditLogService auditLogService,
      UsuarioRepository usuarioRepository) {
    this.subirEvidenciaUseCase = subirEvidenciaUseCase;
    this.consultarEvidenciaUseCase = consultarEvidenciaUseCase;
    this.cambiarEstadoEvidenciaUseCase = cambiarEstadoEvidenciaUseCase;
    this.eliminarEvidenciaUseCase = eliminarEvidenciaUseCase;
    this.marcarObservacionesUseCase = marcarObservacionesUseCase;
    this.auditLogService = auditLogService;
    this.usuarioRepository = usuarioRepository;
  }

  public Evidencia upload(MultipartFile file, Long actaId, UUID callerPublicId) {
    return subirEvidenciaUseCase.execute(file, actaId, callerPublicId);
  }

  public Evidencia get(UUID publicId) {
    return consultarEvidenciaUseCase.get(publicId);
  }

  public Evidencia cambiarEstado(UUID publicId, EstadoEvidencia nuevo) {
    return cambiarEstadoEvidenciaUseCase.execute(publicId, nuevo);
  }

  public void delete(UUID publicId, UUID callerPublicId) {
    eliminarEvidenciaUseCase.execute(publicId, callerPublicId);
  }

  public String presignDownload(UUID publicId) {
    return consultarEvidenciaUseCase.presignDownload(publicId);
  }

  public Evidencia marcarObservaciones(
      UUID evidenciaPublicId,
      List<TipoObservacion> tipos,
      UUID callerPublicId,
      HttpServletRequest request) {
    String ip = extractIp(request);
    return marcarObservacionesUseCase.execute(evidenciaPublicId, tipos, callerPublicId, ip);
  }

  public Evidencia marcarObservaciones(
      UUID evidenciaPublicId, List<TipoObservacion> tipos, UUID callerPublicId, String ipAddress) {
    return marcarObservacionesUseCase.execute(evidenciaPublicId, tipos, callerPublicId, ipAddress);
  }

  /**
   * T-BE-18: subida de evidencia por COORDINATOR/MASTER como contingencia (no es el camino
   * normal — OBSERVER es quien sube). El upload sigue el mismo camino que el OBSERVER
   * (SubirEvidenciaUseCase), pero se registra una fila adicional de auditoría forense
   * con la acción {@code EVIDENCIA_CONTINGENCIA_COORDINADOR} para distinguirla de las
   * subidas regulares. La fila de EVIDENCIA queda con {@code usuario_id} del COORDINATOR
   * (no del OBSERVER que registró el acta) — es trazabilidad para auditoría.
   */
  public Evidencia subirEvidenciaCoordinador(
      Long actaId, MultipartFile imagen, UUID coordinadorPublicId, String ipAddress) {
    Evidencia evidencia = subirEvidenciaUseCase.execute(imagen, actaId, coordinadorPublicId);

    Long coordinadorId =
        usuarioRepository
            .findByPublicId(coordinadorPublicId)
            .map(u -> u.getId())
            .orElse(null);

    auditLogService.registrarConIp(
        coordinadorId,
        "EVIDENCIA_CONTINGENCIA_COORDINADOR",
        "evidencia",
        evidencia.getId(),
        "Subida de contingencia por COORDINATOR/MASTER: acta=" + actaId + ", s3Key=" + evidencia.getS3Key(),
        ipAddress);

    return evidencia;
  }

  public Evidencia subirEvidenciaCoordinador(
      Long actaId, MultipartFile imagen, UUID coordinadorPublicId, HttpServletRequest request) {
    return subirEvidenciaCoordinador(actaId, imagen, coordinadorPublicId, extractIp(request));
  }

  private String extractIp(HttpServletRequest request) {
    if (request == null) return null;
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}

