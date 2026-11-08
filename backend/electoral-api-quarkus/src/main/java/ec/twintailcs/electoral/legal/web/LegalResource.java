package ec.twintailcs.electoral.legal.web;

import ec.twintailcs.electoral.shared.web.ApiErrorResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Documentos legales publicos: politica de privacidad, terminos de uso y version vigente.
 *
 * <p>Las tres rutas son publicas a proposito (spec de privacidad, escenario 4): la informacion
 * obligatoria del GDPR Art. 13/14 tiene que estar disponible para cualquiera, no solo para quien ya
 * tiene sesion.
 *
 * <p>El HTML vive en el classpath ({@code /legal/*.html}), no en la base: un documento legal que se
 * cambia necesita revision y firma en un pull request, no una fila que alguien edito sin dejar
 * rastro. Ver {@code 2026-11-06-t-legal-01-privacy-terms/design.md} (D2).
 */
@Path("/")
public class LegalResource {

  /** Version vigente de los terminos y de la politica. Cambiarla obliga a re-aceptar. */
  @ConfigProperty(name = "legal.terms-version", defaultValue = "1.0")
  String versionTerminos;

  /** Idioma por defecto cuando el cliente no pide uno. */
  private static final String IDIOMA_POR_DEFECTO = "es";

  /** Marcador de version en el HTML legal, sustituido por la version vigente del servidor. */
  static final String MARCADOR_VERSION = "{{VERSION}}";

  /** Idiomas soportados. Cualquier otro cae al por defecto. */
  private static final String[] IDIOMAS = {"es", "en"};

  /**
   * Politica de privacidad (GDPR Art. 13/14).
   *
   * @param lang idioma pedido ({@code es} o {@code en}); si no viene o no es valido, español
   */
  @GET
  @Path("/privacy")
  @Produces(MediaType.TEXT_HTML)
  public Response privacidad(@QueryParam("lang") String lang) {
    return documento("privacy", lang);
  }

  /**
   * Terminos de uso.
   *
   * @param lang idioma pedido ({@code es} o {@code en}); si no viene o no es valido, español
   */
  @GET
  @Path("/terms")
  @Produces(MediaType.TEXT_HTML)
  public Response terminos(@QueryParam("lang") String lang) {
    return documento("terms", lang);
  }

  /**
   * Sirve un documento legal del classpath.
   *
   * <p>El idioma se valida contra una lista cerrada: un {@code lang} arbitrario se usaria para
   * componer el nombre del recurso, y eso es una forma de leer archivos del classpath por
   * parametro.
   *
   * <p>El HTML lleva el marcador {@code {{VERSION}}} en los dos sitios donde aparece el numero de
   * version (cabecera y pie). Se sustituye por la version vigente del servidor para que el
   * documento que el usuario lee y la version que el usuario acepta sean siempre la misma: si el
   * HTML dijera "1.0" hardcodeado y el servidor accepted "1.1", el usuario estaria aceptando un
   * documento que dice ser otro.
   */
  private Response documento(String nombre, String lang) {
    String idioma = normalizarIdioma(lang);
    String recurso = "legal/" + nombre + "-" + idioma + ".html";

    try (InputStream in = LegalResource.class.getClassLoader().getResourceAsStream(recurso)) {
      if (in == null) {
        return Response.status(404)
            .type("application/json")
            .entity(
                ApiErrorResponse.body(
                    404, "Not Found", "No existe el documento legal " + nombre + "-" + idioma))
            .build();
      }
      String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      html = html.replace(MARCADOR_VERSION, versionTerminos);
      return Response.ok(html, MediaType.TEXT_HTML).build();
    } catch (IOException e) {
      return Response.status(500)
          .type("application/json")
          .entity(
              ApiErrorResponse.body(
                  500, "Internal Server Error", "No se pudo leer el documento legal"))
          .build();
    }
  }

  /** Devuelve el idioma pedido si esta soportado; si no, el de por defecto. */
  private static String normalizarIdioma(String lang) {
    if (lang == null || lang.isBlank()) {
      return IDIOMA_POR_DEFECTO;
    }
    String normalizado = lang.trim().toLowerCase();
    for (String soportado : IDIOMAS) {
      if (soportado.equals(normalizado)) {
        return soportado;
      }
    }
    return IDIOMA_POR_DEFECTO;
  }
}
