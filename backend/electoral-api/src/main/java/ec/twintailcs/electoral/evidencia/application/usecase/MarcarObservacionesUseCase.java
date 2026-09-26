package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import ec.twintailcs.electoral.evidencia.domain.ObservacionEvidencia;
import ec.twintailcs.electoral.evidencia.domain.ObservacionEvidenciaRepository;
import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca hasta {@link ObservacionEvidencia#MAX_POR_EVIDENCIA} observaciones sobre una evidencia.
 *
 * <p>Requisitos cubiertos:
 *
 * <ul>
 *   <li>RF-OBS-01: sólo el OBSERVER dueño puede escribir (autoría).
 *   <li>RF-OBS-02: máximo 2 categorías (validado acá y respaldado por trigger en BD).
 *   <li>RF-OBS-03: validación en el servicio, no solo en el setter.
 *   <li>RF-OBS-04: audita vía {@link AuditLogService}.
 *   <li>RF-OBS-05: lista vacía = borra las observaciones actuales.
 *   <li>RF-OBS-06: ADMIN no puede escribir (validado en el controller).
 * </ul>
 */
@Service
public class MarcarObservacionesUseCase {

  private final EvidenciaRepository evidenciaRepository;
  private final ObservacionEvidenciaRepository observacionRepository;
  private final UsuarioRepository usuarioRepository;
  private final AuditLogService auditLogService;
  private final EntityManager entityManager;

  public MarcarObservacionesUseCase(
      EvidenciaRepository evidenciaRepository,
      ObservacionEvidenciaRepository observacionRepository,
      UsuarioRepository usuarioRepository,
      AuditLogService auditLogService,
      EntityManager entityManager) {
    this.evidenciaRepository = evidenciaRepository;
    this.observacionRepository = observacionRepository;
    this.usuarioRepository = usuarioRepository;
    this.auditLogService = auditLogService;
    this.entityManager = entityManager;
  }

  @Transactional
  public Evidencia execute(
      UUID evidenciaPublicId, List<TipoObservacion> tipos, UUID callerPublicId, String ipAddress) {
    // 1) Cargar evidencia
    Evidencia evidencia =
        evidenciaRepository
            .findByPublicId(evidenciaPublicId)
            .orElseThrow(
                () ->
                    new EvidenciaInvalidaException(
                        "Evidencia no encontrada: " + evidenciaPublicId));

    // 2) Autoría (RF-OBS-01)
    Usuario caller =
        usuarioRepository
            .findByPublicId(callerPublicId)
            .orElseThrow(
                () -> new EvidenciaInvalidaException("Usuario no encontrado: " + callerPublicId));
    if (!evidencia.getUsuarioId().equals(caller.getId())) {
      throw new EvidenciaInvalidaException(
          "Solo el OBSERVER que subió la evidencia puede marcar observaciones");
    }

    // 3) Validación de máximo 2 y deduplicación (RF-OBS-02, RF-OBS-03)
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

    // 4) Reemplazar (no acumular) — RF-OBS-05: lista vacía = borrar
    observacionRepository.deleteByEvidenciaId(evidencia.getId());
    entityManager.flush(); // forzar delete antes de insert para no chocar con UNIQUE
    for (TipoObservacion tipo : unicos) {
      ObservacionEvidencia obs = new ObservacionEvidencia();
      obs.setEvidencia(evidencia);
      obs.setTipo(tipo);
      obs.setCreadoEn(Instant.now());
      observacionRepository.save(obs);
    }

    // 5) Audit (RF-OBS-04)
    auditLogService.registrarConIp(
        caller.getId(),
        "EVIDENCIA_OBSERVACIONES_MARCADAS",
        "evidencia",
        evidencia.getId(),
        "Observaciones marcadas: " + unicos.size() + " de " + tipos.size(),
        ipAddress);

    return evidencia;
  }
}
