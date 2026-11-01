package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.evidencia.application.AuditoriaEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * Elimina la evidencia: borra el registro y el objeto de S3.
 *
 * <p>Port exacto del {@code EliminarEvidenciaUseCase} del backend Spring: el delete NO se traga el
 * error de S3 (si S3 falla, el cliente recibe 5xx y puede reintentar) y la eliminacion queda
 * auditada.
 */
@ApplicationScoped
public class EliminarEvidenciaUseCase {

  @Inject S3StorageService s3;
  @Inject AuditoriaEvidencia auditoria;

  /** Elimina la evidencia identificada por su UUID publico. */
  @Transactional
  public void execute(UUID publicId, Long usuarioId, String ip) {
    Evidencia evidencia = Evidencia.find("publicId", publicId).firstResult();
    if (evidencia == null) {
      throw new EvidenciaNotFoundException(publicId);
    }

    Long evidenciaId = evidencia.getId();
    String s3Key = evidencia.getS3Key();

    evidencia.delete();

    // El delete NO debe tragarse el error de S3: si S3 falla, el frontend recibe 5xx y puede
    // reintentar. Antes el catch logueaba y devolvia exito, dejando la foto accesible mientras el
    // usuario pensaba que se habia borrado.
    s3.delete(s3Key);

    auditoria.registrar(
        usuarioId,
        "EVIDENCIA_ELIMINADA",
        evidenciaId,
        "Evidencia eliminada publicId=" + publicId,
        ip);
  }
}
