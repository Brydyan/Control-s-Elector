package ec.twintailcs.electoral.auth.infrastructure.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plantillas HTML del flujo de invitacion.
 *
 * <p>Cada plantilla es un archivo del classpath con marcadores {@code {{variable}}}. El render las
 * sustituye por el valor, escapado.
 *
 * <p>El escapado no es cosmetico: el nombre del usuario invited y el link van dentro de un HTML que
 * se abre en el cliente de correo. Un nombre con {@code <} o unlink con comillas rompe la plantilla
 * y, en el peor caso, permite inyectar markup. Se escapan los cinco caracteres que importan en HTML
 * y en atributos.
 */
public enum EmailTemplates {

  /** Invitacion inicial con el link de activacion. */
  INVITATION("email/invitation.html"),

  /** Confirmacion de que la cuenta ya quedo activada. */
  CONFIRMATION("email/confirmation.html"),

  /** Reenvio de la invitacion. */
  RESEND("email/resend.html");

  private static final Pattern MARCADOR = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

  private final String recurso;

  EmailTemplates(String recurso) {
    this.recurso = recurso;
  }

  /** Ruta del recurso en el classpath. */
  public String recurso() {
    return recurso;
  }

  /**
   * Renderiza la plantilla con las variables dadas.
   *
   * <p>Un marcador sin valor se deja vacio: es preferible un hueco a un {@code {{nombre}}} crudo en
   * el email que ve el usuario.
   *
   * @throws IllegalStateException si la plantilla no esta en el classpath
   */
  public String render(Map<String, String> variables) {
    String html = leer();
    Matcher matcher = MARCADOR.matcher(html);
    StringBuffer salida = new StringBuffer();
    while (matcher.find()) {
      String valor = variables.get(matcher.group(1));
      matcher.appendReplacement(
          salida, Matcher.quoteReplacement(escapar(valor == null ? "" : valor)));
    }
    matcher.appendTail(salida);
    return salida.toString();
  }

  /** Escapa los caracteres con significado en HTML y en atributos. */
  static String escapar(String valor) {
    return valor
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private String leer() {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(recurso)) {
      if (is == null) {
        throw new IllegalStateException("Plantilla de email no encontrada: " + recurso);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer la plantilla de email: " + recurso, e);
    }
  }
}
