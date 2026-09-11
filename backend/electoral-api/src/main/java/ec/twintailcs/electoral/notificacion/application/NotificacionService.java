package ec.twintailcs.electoral.notificacion.application;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.notificacion.domain.Notificacion;
import ec.twintailcs.electoral.notificacion.domain.NotificacionRepository;
import java.util.Objects;
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
    Usuario recipient = Objects.requireNonNull(destinatario, "destinatario is required");
    notif.setDestinatarioId(recipient.getId());
    notif.setTipo(Objects.requireNonNull(tipo, "tipo is required"));
    notif.setActa(acta);
    notif.setMensaje(Objects.requireNonNull(mensaje, "mensaje is required"));
    notif.setLeida(false);
    notificacionRepository.save(notif);
  }
}
