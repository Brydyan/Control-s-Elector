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
        notif.setDestinatario(destinatario);
        notif.setTipo(tipo);
        notif.setActa(acta);
        notif.setMensaje(mensaje);
        notif.setLeida(false);
        notificacionRepository.save(notif);
    }
}
