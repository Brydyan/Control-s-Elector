package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CambiarEstadoEvidenciaUseCase {

  private final EvidenciaRepository repository;

  public CambiarEstadoEvidenciaUseCase(EvidenciaRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Evidencia execute(UUID publicId, EstadoEvidencia nuevo) {
    Evidencia e =
        repository
            .findByPublicId(publicId)
            .orElseThrow(() -> new EvidenciaNotFoundException(publicId));

    if (nuevo != EstadoEvidencia.APROBADA && nuevo != EstadoEvidencia.RECHAZADA) {
      throw new EvidenciaInvalidaException(
          "Solo APROBADA o RECHAZADA son aceptables, recibido: " + nuevo);
    }

    e.setEstado(nuevo);
    e.setActualizadoEn(Instant.now());
    return repository.save(e);
  }
}
