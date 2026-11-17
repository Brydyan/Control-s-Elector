package ec.twintailcs.electoral.auth.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Plantillas HTML del email de invitacion.
 *
 * <p>Dos cosas importan acá. La primera es que la plantilla avise que el link expira en 24 horas:
 * el usuario que llega tarde tiene que saber si el problema es que tardo o que el link murio, y sin
 * ese aviso no puede distinguirlo. La segunda es que los valores que se interpolan queden
 * escapados: el nombre del usuario y el link van dentro de un HTML que abre el cliente de correo, y
 * un valor con una comilla o un corchete rompe la plantilla.
 */
class EmailTemplatesTest {

  private static Map<String, String> variables() {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("nombre", "Ana");
    variables.put("apellido", "Perez");
    variables.put("nombreCompleto", "Ana Perez");
    variables.put("rol", "OBSERVER");
    variables.put("username", "juagarobs@control.ttcs");
    variables.put("usuarioCorto", "juagarobs");
    variables.put("emailPersonal", "juan.garcia@example.com");
    variables.put("activationLink", "http://localhost:4200/auth/activate?token=abc.def.ghi");
    variables.put("tokenExpira", "2026-11-07T06:52:43Z");
    return variables;
  }

  @Test
  @DisplayName("la invitación avisa que el link expira en 24 horas")
  void laInvitacionAvisaQueElLinkExpiraEn24Horas() {
    String html = EmailTemplates.INVITATION.render(variables());

    assertThat(html)
        .as("el aviso de expiración debería estar en la plantilla")
        .contains("24 horas");
  }

  @Test
  @DisplayName("la invitación trae el link de activación y el rol del usuario")
  void laInvitacionTraeElLinkYElRol() {
    String html = EmailTemplates.INVITATION.render(variables());

    assertThat(html).contains("http://localhost:4200/auth/activate?token=abc.def.ghi");
    assertThat(html).contains("OBSERVER");
    assertThat(html).contains("Ana Perez");
  }

  @Test
  @DisplayName("la invitación no deja marcadores sin sustituir")
  void laInvitacionNoDejaMarcadoresSinSustituir() {
    String html = EmailTemplates.INVITATION.render(variables());

    assertThat(html).as("quedó un marcador sin sustituir").doesNotContain("{{");
  }

  @Test
  @DisplayName("un valor con HTML queda escapado")
  void unValorConHtmlQuedaEscapado() {
    Map<String, String> variables = variables();
    variables.put("nombreCompleto", "<script>alert(1)</script>");

    String html = EmailTemplates.INVITATION.render(variables);

    assertThat(html).as("el script no debe quedar crudo").doesNotContain("<script>");
    assertThat(html).contains("&lt;script&gt;");
  }

  @Test
  @DisplayName("un valor con comillas queda escapado")
  void unValorConComillasQuedaEscapado() {
    Map<String, String> variables = variables();
    variables.put("nombreCompleto", "Ana \"la Jefa\" Perez");

    String html = EmailTemplates.INVITATION.render(variables);

    assertThat(html).contains("&quot;");
  }

  @Test
  @DisplayName("una variable ausente queda vacía, no el marcador crudo")
  void unaVariableAusenteQuedaVacia() {
    Map<String, String> variables = variables();
    variables.remove("rol");

    String html = EmailTemplates.INVITATION.render(variables());

    assertThat(html).doesNotContain("{{rol}}");
    assertThat(html).doesNotContain("{{");
  }

  @Test
  @DisplayName("la confirmación y el reenvío también son plantillas completas")
  void laConfirmacionYElReenvioSonPlantillasCompletas() {
    assertThat(EmailTemplates.CONFIRMATION.render(variables()))
        .contains("Ana Perez")
        .doesNotContain("{{");
    assertThat(EmailTemplates.RESEND.render(variables())).contains("24 horas").doesNotContain("{{");
  }

  @Test
  @DisplayName("el email no lleva contraseña temporal: lleva un enlace de activación")
  void elEmailNoLlevaContrasenaTemporal() {
    String html = EmailTemplates.INVITATION.render(variables());

    assertThat(html)
        .as("la invitación no debe mandar una contraseña temporal")
        .doesNotContain("contraseña temporal")
        .doesNotContain("Contraseña temporal");
    assertThat(html).contains("Activar Cuenta y Establecer Contraseña");
    assertThat(html).contains("http://localhost:4200/auth/activate?token=abc.def.ghi");
  }

  @Test
  @DisplayName("el email distingue el usuario de Control del correo personal")
  void elEmailDistingueElUsuarioDeControlDelCorreoPersonal() {
    String html = EmailTemplates.INVITATION.render(variables());

    assertThat(html)
        .as("el usuario de Control y el correo personal son datos distintos")
        .contains("juagarobs@control.ttcs")
        .contains("juan.garcia@example.com")
        .contains("Tu usuario (Control)")
        .contains("Tu correo personal");
  }

  @Test
  @DisplayName("el email separa el usuario de Control, su correo y el correo personal")
  void elEmailSeparaUsuarioCorreoControlYCorreoPersonal() {
    String html = EmailTemplates.INVITATION.render(variables());

    // Son tres datos: el usuario que la persona reconoce ("juagarobs"), el
    // correo con el que entra ("juagarobs@control.ttcs") y el correo donde se
    // le escribe ("juan.garcia@example.com").
    assertThat(html)
        .as("el usuario de Control va sin dominio")
        .contains("Tu usuario (Control):")
        .contains("juagarobs")
        .contains("Tu correo (Control):")
        .contains("juagarobs@control.ttcs")
        .contains("Tu correo personal:");
  }

  @Test
  @DisplayName("el email ofrece activar y establecer contraseña, y dice a quién avisar")
  void elEmailOfreceActivarYDiceAQuienAvisar() {
    String html = EmailTemplates.INVITATION.render(variables());

    assertThat(html)
        .as("el botón dice lo que va a pasar")
        .contains("Activar Cuenta y Establecer Contraseña");
    assertThat(html)
        .as("si la persona no pidió la cuenta, tiene a quién avisar")
        .contains("Si no solicitaste esta cuenta");
    assertThat(html).contains("Contacta al equipo de Control's Elector");
  }

  @Test
  @DisplayName("el reenvío también distingue usuario de Control y correo personal")
  void elReenvioTambienDistingueLosDosCorreos() {
    String html = EmailTemplates.RESEND.render(variables());

    assertThat(html)
        .contains("juagarobs@control.ttcs")
        .contains("juan.garcia@example.com")
        .contains("24 horas");
  }

  @Test
  @DisplayName("la invitación explica que la cuenta está creada pero no activa")
  void laInvitacionExplicaQueLaCuentaEstaCreadaPeroNoActiva() {
    String html = EmailTemplates.INVITATION.render(variables());

    // Sin esto la persona no entiende por qué tiene que activar: da por hecho que
    // la cuenta ya está lista para entrar.
    assertThat(html)
        .as("la invitación debe decir que la cuenta ya existe")
        .contains("Cuenta creada");
    assertThat(html)
        .as("la invitación debe decir que la activación sigue pendiente")
        .contains("Activación pendiente");
    assertThat(html)
        .as("la invitación debe decir que sin activar no se puede entrar")
        .contains("Todavía no podés entrar");
  }

  @Test
  @DisplayName("el reenvío explica que la cuenta sigue sin activarse")
  void elReenvioExplicaQueLaCuentaSigueSinActivarse() {
    String html = EmailTemplates.RESEND.render(variables());

    assertThat(html)
        .as("el reenvío debe decir que la cuenta sigue pendiente")
        .contains("Activación pendiente")
        .contains("sigue sin activarse");
  }

  @Test
  @DisplayName("los correos usan la marca de la plataforma y no la de un organismo")
  void losCorreosUsanLaMarcaDeLaPlataforma() {
    String invitacion = EmailTemplates.INVITATION.render(variables());
    String reenvio = EmailTemplates.RESEND.render(variables());
    String confirmacion = EmailTemplates.CONFIRMATION.render(variables());

    // La plataforma se llama Control's Elector: no está ligada a ningún
    // organismo electoral y el correo no puede insinuar lo contrario.
    assertThat(invitacion).contains("Control's Elector").doesNotContain("CNE");
    assertThat(reenvio).contains("Control's Elector").doesNotContain("CNE");
    assertThat(confirmacion).contains("Control's Elector").doesNotContain("CNE");
  }
}
