package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleCompletoDto;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.dto.mapper.ActaMapper;
import ec.twintailcs.electoral.auth.domain.RecursoNoEncontradoException;
import ec.twintailcs.electoral.evidencia.application.usecase.ConsultarEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.panache.common.Sort;
import java.util.List;
import java.util.UUID;

/**
 * Arma el detalle completo del acta: votos por candidato, evidencia y motivo de disputa.
 *
 * <p>El listado plano (sin colecciones) sigue en otro endpoint: este caso de uso es para la vista de
 * detalle, donde SI se cargan las colecciones.
 */
@ApplicationScoped
public class ConsultarActaDetalleUseCase {

  @Inject ActaRepository repository;
  @Inject ActaMapper mapper;
  @Inject ConsultarEvidenciaUseCase evidenciaUseCase;

  /**
   * Detalle completo del acta.
   *
   * @param actaId id interno del acta
   */
  @Transactional
  public ActaDetalleCompletoDto ejecutar(Long actaId) {
    Acta acta =
        repository
            .buscarPorId(actaId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Acta no encontrada: " + actaId));

    ActaRepository.ProyeccionActa fila =
        repository
            .proyeccionPorId(actaId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Acta no encontrada: " + actaId));

    List<ActaDetalleDto> detalles =
        repository.detallesDe(actaId).stream().map(mapper::toDetalleDto).toList();

    Evidencia evidencia = primeraEvidencia(actaId);
    UUID evidenciaPublicId = evidencia == null ? null : evidencia.getPublicId();
    String urlEvidencia = null;
    if (evidencia != null) {
      try {
        urlEvidencia = evidenciaUseCase.presignDownload(evidencia.getPublicId());
      } catch (Exception ignored) {
        // No propagamos: el DTO se entrega con urlEvidencia=null si S3 falla.
      }
    }

    String motivoDisputa =
        (acta.getEstado() == EstadoActa.EN_DISPUTA)
            ? repository.ultimoMotivoDisputa(actaId).orElse(null)
            : null;

    long totalVotos = detalles.stream().mapToLong(d -> d.votos() == null ? 0L : d.votos()).sum();

    return new ActaDetalleCompletoDto(
        fila.id(),
        fila.jrvId(),
        fila.cargoId(),
        fila.registradaPorId(),
        fila.estado(),
        fila.totalSufragantes(),
        fila.votosBlancos(),
        fila.votosNulos(),
        fila.fechaRegistro(),
        fila.motivoAnulacion(),
        fila.nombreCargo(),
        fila.numeroJrv(),
        totalVotos,
        detalles,
        evidenciaPublicId,
        urlEvidencia,
        motivoDisputa);
  }

  /** Primera evidencia asociada al acta (la que se muestra en el detalle). */
  private Evidencia primeraEvidencia(Long actaId) {
    return Evidencia.find("actaId", Sort.by("id"), actaId).firstResult();
  }
}
