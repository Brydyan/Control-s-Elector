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


    public void recordLogin(Long usuarioId, String ip) { recordAuth(usuarioId, "login", ip); }
    public void recordLoginFail(String email, String ip) { recordAuth(null, "login_fail", ip); }
    public void recordRefresh(Long usuarioId) { recordAuth(usuarioId, "refresh", null); }
    public void recordRefreshFail(String ip) { recordAuth(null, "refresh_fail", ip); }
    public void recordLogout(Long usuarioId) { recordAuth(usuarioId, "logout", null); }

    @Transactional
    private void recordAuth(Long usuarioId, String accion, String ip) {
        AuditLog entry = new AuditLog();
        entry.setEntidad("auth"); entry.setEntidadId(usuarioId == null ? 0L : usuarioId);
        entry.setAccion(accion); entry.setDetalle("{}"); entry.setIpAddress(ip); entry.setRegistradoPor(usuarioId);
        auditLogRepository.save(entry);
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
