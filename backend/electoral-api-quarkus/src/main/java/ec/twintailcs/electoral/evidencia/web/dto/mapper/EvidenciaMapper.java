package ec.twintailcs.electoral.evidencia.web.dto.mapper;

import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.ObservacionEvidencia;
import ec.twintailcs.electoral.evidencia.web.dto.EvidenciaResponse;
import ec.twintailcs.electoral.evidencia.web.dto.ObservacionDto;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Mapeo entre la entidad {@link Evidencia} y el DTO {@link EvidenciaResponse}.
 *
 * <p>El unico mapeo no trivial es {@code publicId -> id}; el resto de los campos machean por
 * nombre.
 *
 * <p><b>Las observaciones se leen de la base, no de la entidad.</b> Igual que hace el mapper del
 * backend Spring (que consulta el repositorio de observaciones), y no el campo de la entidad: asi
 * el DTO ve siempre lo que esta persistido, incluso en la misma transaccion en la que se acaban de
 * marcar. El mapper se invoca DENTRO de la transaccion, asi que la consulta usa la misma conexion y
 * el mismo contexto de RLS.
 */
@ApplicationScoped
public class EvidenciaMapper {

  /** DTO de una evidencia, con sus observaciones. */
  public EvidenciaResponse toDto(Evidencia entity) {
    if (entity == null) {
      return null;
    }
    List<ObservacionDto> observaciones =
        ObservacionEvidencia.getEntityManager()
            .createQuery(
                "from ObservacionEvidencia where evidenciaId = :evidenciaId order by id",
                ObservacionEvidencia.class)
            .setParameter("evidenciaId", entity.getId())
            .getResultList()
            .stream()
            .map(
                observacion -> new ObservacionDto(observacion.getTipo(), observacion.getCreadoEn()))
            .toList();

    return new EvidenciaResponse(
        entity.getPublicId(),
        entity.getJrvId(),
        entity.getCargoId(),
        entity.getUsuarioId(),
        entity.getS3Key(),
        entity.getSizeBytes(),
        entity.getContentType(),
        entity.getEstado(),
        entity.getCreadoEn(),
        entity.getActualizadoEn(),
        observaciones);
  }
}
