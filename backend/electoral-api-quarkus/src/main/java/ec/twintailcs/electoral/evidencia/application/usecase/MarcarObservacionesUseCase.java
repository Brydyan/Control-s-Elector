package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.evidencia.application.AuditoriaEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.evidencia.domain.ObservacionEvidencia;
import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Marca hasta {@link ObservacionEvidencia#MAX_POR_EVIDENCIA} observaciones sobre una evidencia.
 *
 * <p>Requisitos cubiertos:
 *
 * <ul>
 *   <li>RF-OBS-01: solo el OBSERVER dueno puede escribir (autoria).
 *   <li>RF-OBS-02: maximo 2 categorias (validado aca y respaldado con trigger en la base).
 *   <li>RF-OBS-03: validacion en el caso de uso, no solo en el setter.
 *   <li>RF-OBS-04: audita el cambio.
 *   <li>RF-OBS-05: lista vacia = borra las observaciones actuales.
 *   <li>RF-OBS-06: ADMIN no puede escribir (validado en el resource).
 * </ul>
 */
@ApplicationScoped
public class MarcarObservacionesUseCase {

  @Inject AuditoriaEvidencia auditoria;

  /**
   * Reemplaza las observaciones de la evidencia por las indicadas.
   *
   * @param evidenciaPublicId UUID publico de la evidencia
   * @param tipos categorias a marcar (vacia o {@code null} = borrar todas)
   * @param usuarioId id interno del usuario que marca
   * @param ip direccion IP del cliente
   */
  @Transactional
  public Evidencia execute(
      UUID evidenciaPublicId, List<TipoObservacion> tipos, Long usuarioId, String ip) {
    // 1) Cargar evidencia
    Evidencia evidencia = Evidencia.find("publicId", evidenciaPublicId).firstResult();
    if (evidencia == null) {
      throw new EvidenciaInvalidaException("Evidencia no encontrada: " + evidenciaPublicId);
    }

    // 2) Autoria (RF-OBS-01): solo el OBSERVER que subio la evidencia puede marcar observaciones.
    if (evidencia.getUsuarioId() == null || !evidencia.getUsuarioId().equals(usuarioId)) {
      throw new EvidenciaInvalidaException(
          "Solo el OBSERVER que subió la evidencia puede marcar observaciones");
    }

    // 3) Validacion de maximo 2 y deduplicacion (RF-OBS-02, RF-OBS-03)
    if (tipos == null) {
      tipos = List.of();
    }
    Set<TipoObservacion> unicos = new LinkedHashSet<>(tipos);
    if (unicos.size() > ObservacionEvidencia.MAX_POR_EVIDENCIA) {
      throw new EvidenciaInvalidaException(
          "Máximo "
              + ObservacionEvidencia.MAX_POR_EVIDENCIA
              + " observaciones por evidencia (recibidas: "
              + tipos.size()
              + ")");
    }

    // 4) Reemplazar (no acumular) — RF-OBS-05: lista vacia = borrar
    ObservacionEvidencia.delete("evidenciaId = ?1", evidencia.getId());
    ObservacionEvidencia.getEntityManager().flush();
    for (TipoObservacion tipo : unicos) {
      ObservacionEvidencia observacion = new ObservacionEvidencia();
      observacion.setEvidenciaId(evidencia.getId());
      observacion.setTipo(tipo);
      observacion.setCreadoEn(Instant.now());
      observacion.persist();
    }

    // 5) Auditoria (RF-OBS-04)
    auditoria.registrar(
        usuarioId,
        "EVIDENCIA_OBSERVACIONES_MARCADAS",
        evidencia.getId(),
        "Observaciones marcadas: " + unicos.size() + " de " + tipos.size(),
        ip);

    return evidencia;
  }
}
