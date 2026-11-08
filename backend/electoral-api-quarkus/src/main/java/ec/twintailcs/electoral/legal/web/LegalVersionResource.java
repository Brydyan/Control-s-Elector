package ec.twintailcs.electoral.legal.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Version vigente de los documentos legales.
 *
 * <p>Es publica a proposito: la version de un documento no es informacion sensible, y asi el modal
 * de aceptacion puede decidir si hace falta pedir la aceptacion antes de tener sesion.
 *
 * <p><b>Por que es una clase aparte y no un metodo de {@link LegalResource}.</b> JAX-RS elige el
 * recurso raíz cuyo camino coincide mas largo. {@code DpoResource} esta en {@code /api/v1/legal} y
 * {@code LegalResource} en {@code /}: un metodo con {@code @Path("/api/v1/legal/current-version")}
 * dentro de {@code LegalResource} pierde contra {@code DpoResource}, que se lleva la peticion y no
 * tiene ese metodo — y la respuesta es un 404. Con la ruta completa como camino raíz de la clase,
 * el empate se resuelve a favor de la ruta mas larga y el endpoint responde.
 */
@Path("/api/v1/legal/current-version")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class LegalVersionResource {

  /** Version vigente de los terminos y de la politica. Cambiarla obliga a re-aceptar. */
  @ConfigProperty(name = "legal.terms-version", defaultValue = "1.0")
  String versionTerminos;

  @GET
  public Response versionVigente() {
    Map<String, String> body = new LinkedHashMap<>();
    // `version` es la version vigente del bloque legal completo; las dos especificas
    // viajan tambien porque terminos y politica se pueden versionar por separado
    // sin que el frontend tenga que suponer que son la misma.
    body.put("version", versionTerminos);
    body.put("terms_version", versionTerminos);
    body.put("privacy_version", versionTerminos);
    return Response.ok(body).build();
  }
}
