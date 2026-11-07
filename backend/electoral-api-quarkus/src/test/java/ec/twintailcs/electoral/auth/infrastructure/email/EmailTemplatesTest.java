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
}
