package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;

/**
 * Transiciona la evidencia a APROBADA o RECHAZADA.
 *
 * <p>Port exacto del {@code CambiarEstadoEvidenciaUseCase} del backend Spring: el estado de
 * destino se valida en el caso de uso (no en el setter), y el cambio queda persistido.
 */
@ApplicationScoped
public class CambiarEstadoEvidenciaUseCase {

  /** Transiciona la evidencia al estado pedido. */
  @Transactional
  public Evidencia execute(UUID publicId, EstadoEvidencia nuevo) {
    Evidencia evidencia = Evidencia.find("publicId", publicId).firstResult();
    if (evidencia == null) {
      throw new EvidenciaNotFoundException(publicId);
    }

    if (nuevo != EstadoEvidencia.APROBADA && nuevo != EstadoEvidencia.RECHAZADA) {
      throw new EvidenciaInvalidaException(
          "Solo APROBADA o RECHAZADA son aceptables, recibido: " + nuevo);
    }

    evidencia.setEstado(nuevo);
    evidencia.setActualizadoEn(Instant.now());
    return evidencia;
  }
}
