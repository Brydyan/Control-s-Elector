package ec.twintailcs.electoral.notificacion.application;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.notificacion.domain.Notificacion;
import ec.twintailcs.electoral.notificacion.domain.NotificacionRepository;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacionService {

    private static final Logger log = Logger.getLogger(NotificacionService.class.getName());

    private final NotificacionRepository notificacionRepository;

    public NotificacionService(NotificacionRepository notificacionRepository) {
        this.notificacionRepository = notificacionRepository;
    }

    @Transactional
    public void notificar(Usuario destinatario, String tipo, Acta acta, String mensaje) {
        Notificacion notif = new Notificacion();
        notif.setDestinatarioId(destinatario.getId());
        // tipo + acta van serializados al mensaje para mantener el modelo
        // de Notificacion estable. Si más adelante hace falta, se agregan
        // columnas dedicadas en otro PR.
        String fullMensaje = "[" + tipo + "]" + (acta != null ? " (acta " + acta.getId() + ")" : "") + " " + mensaje;
        notif.setMensaje(fullMensaje);
        notif.setLeida(false);
        notificacionRepository.save(notif);
    }
}
