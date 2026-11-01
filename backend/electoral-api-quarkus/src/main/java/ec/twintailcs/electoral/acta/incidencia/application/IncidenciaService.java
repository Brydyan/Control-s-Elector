package ec.twintailcs.electoral.acta.incidencia.application;

import ec.twintailcs.electoral.acta.application.ActaAuditService;
import ec.twintailcs.electoral.acta.incidencia.domain.IncidenciaJrv;
import ec.twintailcs.electoral.acta.incidencia.domain.IncidenciaRepository;
import ec.twintailcs.electoral.acta.incidencia.web.dto.IncidenciaRequestDto;
import ec.twintailcs.electoral.acta.incidencia.web.dto.IncidenciaResponseDto;
import ec.twintailcs.electoral.auth.domain.RecursoNoEncontradoException;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Registro y listado de incidencias operativas de JRV (T-BE-19).
 *
 * <p>Port exacto del {@code IncidenciaService} del backend Spring: al persistir una incidencia se
 * emite un evento de auditoria para que los monitores del dashboard lo consulten.
 */
@ApplicationScoped
public class IncidenciaService {

  @Inject IncidenciaRepository repository;
  @Inject ContextoSolicitud contexto;
  @Inject ActaAuditService audit;

  /**
   * Registra una incidencia.
   *
   * @param dto datos de la incidencia
   * @param ipAddress IP del cliente
   */
  @Transactional
  public IncidenciaResponseDto registrar(IncidenciaRequestDto dto, String ipAddress) {
    if (!repository.existeJrv(dto.jrvId())) {
      throw new RecursoNoEncontradoException("JRV no encontrada: " + dto.jrvId());
    }
    Long reportadaPorId = contexto.usuarioId();

    IncidenciaJrv incidencia = new IncidenciaJrv();
    incidencia.setJrvId(dto.jrvId());
    incidencia.setTipo(dto.tipo());
    incidencia.setDescripcion(dto.descripcion());
    incidencia.setEvidenciaUrl(dto.evidenciaUrl());
    incidencia.setReportadaPorId(reportadaPorId);
    incidencia.persist();

    audit.registrarConIp(
        reportadaPorId,
        "INCIDENCIA_JRV_REGISTRADA",
        "incidencia_jrv",
        incidencia.getId(),
        "Incidencia " + dto.tipo() + " en JRV " + dto.jrvId(),
        ipAddress);

    return new IncidenciaResponseDto(
        incidencia.getId(),
        incidencia.getJrvId(),
        incidencia.getTipo(),
        incidencia.getDescripcion(),
        incidencia.getEvidenciaUrl(),
        incidencia.getReportadaPorId(),
        incidencia.getFechaRegistro());
  }

  /** Listado de incidencias, con filtro opcional por JRV. */
  @Transactional
  public List<IncidenciaResponseDto> listar(Long jrvId) {
    return repository.listar(jrvId);
  }
}
