package ec.twintailcs.electoral.auditlog.application;

import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import ec.twintailcs.electoral.auditlog.domain.AuditLogRepository;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = Logger.getLogger(AuditLogService.class.getName());

    private final AuditLogRepository auditLogRepository;
    private final UsuarioRepository usuarioRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, UsuarioRepository usuarioRepository) {
        this.auditLogRepository = auditLogRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public void registrar(Long usuarioId,
                          String accion,
                          String entidad,
                          Long entidadId,
                          String detalle,
                          HttpServletRequest request) {
        AuditLog log = new AuditLog();

        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId)
                    .ifPresent(usuario -> log.setRegistradoPor(usuario.getId()));
        }

        log.setAccion(accion);
        log.setEntidad(entidad);
        log.setEntidadId(entidadId);
        // detalle human-readable -> payload JSON estructurado para JSONB.
        // Null se mantiene como SQL NULL (no se serializa a {}).
        log.setDetalle(toJsonPayload(detalle));
        log.setIpAddress(obtenerIpReal(request));

        auditLogRepository.save(log);
    }

    @Transactional
    public void registrar(Long usuarioId, String accion, String entidad, Long entidadId, String detalle) {
        registrar(usuarioId, accion, entidad, entidadId, detalle, null);
    }

    /**
     * Envuelve un detalle human-readable en un payload JSON estructurado
     * para persistir en {@code audit_log.detalle} (JSONB). Null se
     * mantiene como SQL NULL, no como un objeto vacío. No concatena raw
     * JSON: usa un {@code Map} que Hibernate
     * serializa vía {@code SqlTypes#JSON}.
     */
    private Map<String, Object> toJsonPayload(String detalle) {
        if (detalle == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", detalle);
        return payload;
    }

    private String obtenerIpReal(HttpServletRequest request) {
        if (request == null) return "sistema";

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}