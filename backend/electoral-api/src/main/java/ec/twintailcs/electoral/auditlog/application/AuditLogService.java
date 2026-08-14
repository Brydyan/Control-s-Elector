package ec.twintailcs.electoral.auditlog.application;

import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import ec.twintailcs.electoral.auditlog.domain.AuditLogRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
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
            usuarioRepository.findById(usuarioId).ifPresent(log::setUsuario);
        }

        log.setAccion(accion);
        log.setEntidad(entidad);
        log.setEntidadId(entidadId);
        log.setDetalle(detalle);
        log.setIpAddress(obtenerIpReal(request));

        auditLogRepository.save(log);
    }

    @Transactional
    public void registrar(Long usuarioId, String accion, String entidad, Long entidadId, String detalle) {
        registrar(usuarioId, accion, entidad, entidadId, detalle, null);
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
