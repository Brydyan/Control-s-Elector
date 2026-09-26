package ec.twintailcs.electoral.incidencia.application;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.JrvRepository;
import ec.twintailcs.electoral.incidencia.domain.IncidenciaJrv;
import ec.twintailcs.electoral.incidencia.domain.IncidenciaJrvRepository;
import ec.twintailcs.electoral.incidencia.web.dto.IncidenciaRequestDto;
import ec.twintailcs.electoral.incidencia.web.dto.IncidenciaResponseDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio para registrar y listar incidencias operativas de JRV (T-BE-19).
 *
 * <p>Per design.md D3: al persistir una incidencia, se emite un evento via
 * {@link AuditLogService} para que los monitores del dashboard lo consulten.
 */
@Service
public class IncidenciaService {

  private final IncidenciaJrvRepository incidenciaRepository;
  private final JrvRepository jrvRepository;
  private final UsuarioRepository usuarioRepository;
  private final AuditLogService auditLogService;

  public IncidenciaService(
      IncidenciaJrvRepository incidenciaRepository,
      JrvRepository jrvRepository,
      UsuarioRepository usuarioRepository,
      AuditLogService auditLogService) {
    this.incidenciaRepository = incidenciaRepository;
    this.jrvRepository = jrvRepository;
    this.usuarioRepository = usuarioRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public IncidenciaJrv registrarIncidencia(IncidenciaRequestDto dto, UUID reporterPublicId, String ipAddress) {
    Jrv jrv =
        jrvRepository
            .findById(dto.jrvId())
            .orElseThrow(
                () -> new EntityNotFoundException("JRV no encontrada: " + dto.jrvId()));

    Usuario reporter =
        usuarioRepository
            .findByPublicId(reporterPublicId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Usuario autenticado no encontrado: " + reporterPublicId));

    IncidenciaJrv inc = new IncidenciaJrv();
    inc.setJrv(jrv);
    inc.setTipo(dto.tipo());
    inc.setDescripcion(dto.descripcion());
    inc.setEvidenciaUrl(dto.evidenciaUrl());
    inc.setReportadaPor(reporter);
    IncidenciaJrv saved = incidenciaRepository.save(inc);

    auditLogService.registrarConIp(
        reporter.getId(),
        "INCIDENCIA_JRV_REGISTRADA",
        "incidencia_jrv",
        saved.getId(),
        "Incidencia " + dto.tipo() + " en JRV " + dto.jrvId(),
        ipAddress);

    return saved;
  }

  public IncidenciaJrv registrarIncidencia(IncidenciaRequestDto dto, UUID reporterPublicId, HttpServletRequest request) {
    return registrarIncidencia(dto, reporterPublicId, extractIp(request));
  }

  private String extractIp(HttpServletRequest request) {
    if (request == null) return null;
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
    return request.getRemoteAddr();
  }

  @Transactional(readOnly = true)
  public List<IncidenciaResponseDto> listarIncidencias(Long jrvId) {
    List<IncidenciaJrv> list =
        (jrvId == null)
            ? incidenciaRepository.findAllByOrderByFechaRegistroDesc()
            : incidenciaRepository.findByJrvIdOrderByFechaRegistroDesc(jrvId);
    return list.stream().map(this::toDto).toList();
  }

  private IncidenciaResponseDto toDto(IncidenciaJrv inc) {
    return new IncidenciaResponseDto(
        inc.getId(),
        inc.getJrv().getId(),
        inc.getTipo(),
        inc.getDescripcion(),
        inc.getEvidenciaUrl(),
        inc.getReportadaPor().getId(),
        inc.getFechaRegistro());
  }
}
