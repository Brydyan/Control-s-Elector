package ec.twintailcs.electoral.auth.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El proveedor SMTP arma el mensaje y se lo entrega al mailer.
 *
 * <p>El mailer se dobla con un doble que guarda los mensajes: lo que importa no es que el correo
 * llegue —eso lo decide el SMTP de cada entorno— sino que el mensaje que sale tenga el destinatario
 * correcto, la plantilla correcta y el link de activacion dentro.
 *
 * <p>El link importa en particular: es lo que separa "el usuario puede activar su cuenta" de "el
 * usuario recibio un correo decorativo". Un link mal formado no se ve en el correo hasta que el
 * usuario lo pulsa y falla.
 */
class SmtpEmailProviderTest {

  /** Mailer que guarda lo que se le entrega. */
  private static final class MailerDoble implements Mailer {
    private final List<Mail> enviados = new ArrayList<>();

    @Override
    public void send(Mail... mails) {
      for (Mail mail : mails) {
        enviados.add(mail);
      }
    }
  }

  private static EmailAEnviar invitacion(EmailAEnviar.TipoEmail tipo) {
    return new EmailAEnviar(
        7L,
        "ana@control.ttcs",
        "Ana",
        "Perez",
        "OBSERVER",
        "http://localhost:4200/auth/activate?token=abc.def.ghi",
        "2026-11-07T06:52:43Z",
        1,
        tipo);
  }

  @Test
  @DisplayName("la invitación se envía con el destinatario y el link de activación")
  void laInvitacionSeEnviaConElDestinatarioYElLink() {
    MailerDoble mailer = new MailerDoble();
    SmtpEmailProvider provider = new SmtpEmailProvider(mailer, "noreply@control.electoral.ec");

    provider.sendInvitationEmail(invitacion(EmailAEnviar.TipoEmail.INVITACION));

    assertThat(mailer.enviados).as("debería haberse enviado un email").hasSize(1);
    Mail mail = mailer.enviados.get(0);
    assertThat(mail.getTo()).containsExactly("ana@control.ttcs");
    assertThat(mail.getFrom()).isEqualTo("noreply@control.electoral.ec");
    assertThat(mail.getHtml())
        .as("el cuerpo debería traer el link")
        .contains("http://localhost:4200/auth/activate?token=abc.def.ghi");
    assertThat(mail.getHtml()).as("y el aviso de expiración").contains("24 horas");
  }

  @Test
  @DisplayName("el reenvío usa la plantilla de reenvío, no la de invitación")
  void elReenvioUsaLaPlantillaDeReenvio() {
    MailerDoble mailer = new MailerDoble();
    SmtpEmailProvider provider = new SmtpEmailProvider(mailer, "noreply@control.electoral.ec");

    provider.sendInvitationEmail(invitacion(EmailAEnviar.TipoEmail.REENVIO));

    assertThat(mailer.enviados.get(0).getHtml())
        .as("el reenvío tiene que decir que el link anterior ya no sirve")
        .contains("ya no sirve");
  }

  @Test
  @DisplayName("la confirmación de activación saluda al usuario por su nombre")
  void laConfirmacionDeActivacionSaludaAlUsuarioPorSuNombre() {
    MailerDoble mailer = new MailerDoble();
    SmtpEmailProvider provider = new SmtpEmailProvider(mailer, "noreply@control.electoral.ec");

    provider.sendActivationConfirmation("ana@control.ttcs", "Ana Perez");

    assertThat(mailer.enviados).hasSize(1);
    Mail mail = mailer.enviados.get(0);
    assertThat(mail.getTo()).containsExactly("ana@control.ttcs");
    assertThat(mail.getHtml()).contains("Ana Perez");
  }
}
