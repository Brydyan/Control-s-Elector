package ec.twintailcs.electoral.evidencia.application;

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

  public EvidenciaService(
      SubirEvidenciaUseCase subirEvidenciaUseCase,
      ConsultarEvidenciaUseCase consultarEvidenciaUseCase,
      CambiarEstadoEvidenciaUseCase cambiarEstadoEvidenciaUseCase,
      EliminarEvidenciaUseCase eliminarEvidenciaUseCase,
      MarcarObservacionesUseCase marcarObservacionesUseCase) {
    this.subirEvidenciaUseCase = subirEvidenciaUseCase;
    this.consultarEvidenciaUseCase = consultarEvidenciaUseCase;
    this.cambiarEstadoEvidenciaUseCase = cambiarEstadoEvidenciaUseCase;
    this.eliminarEvidenciaUseCase = eliminarEvidenciaUseCase;
    this.marcarObservacionesUseCase = marcarObservacionesUseCase;
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

  private String extractIp(HttpServletRequest request) {
    if (request == null) return null;
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}

