package ec.twintailcs.electoral.evidencia.application;

import ec.twintailcs.electoral.evidencia.application.usecase.CambiarEstadoEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.ConsultarEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.EliminarEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.SubirEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
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

  public EvidenciaService(
      SubirEvidenciaUseCase subirEvidenciaUseCase,
      ConsultarEvidenciaUseCase consultarEvidenciaUseCase,
      CambiarEstadoEvidenciaUseCase cambiarEstadoEvidenciaUseCase,
      EliminarEvidenciaUseCase eliminarEvidenciaUseCase) {
    this.subirEvidenciaUseCase = subirEvidenciaUseCase;
    this.consultarEvidenciaUseCase = consultarEvidenciaUseCase;
    this.cambiarEstadoEvidenciaUseCase = cambiarEstadoEvidenciaUseCase;
    this.eliminarEvidenciaUseCase = eliminarEvidenciaUseCase;
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
}
