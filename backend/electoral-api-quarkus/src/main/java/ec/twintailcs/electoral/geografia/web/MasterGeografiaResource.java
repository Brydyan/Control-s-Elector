package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.geografia.application.GeografiaService;
import ec.twintailcs.electoral.geografia.web.dto.GeografiaTreeDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Arbol geografico electoral, en {@code GET /api/v1/master/geografia}.
 *
 * <p>Lectura: la pueden hacer MASTER y ADMIN (permiso {@code configuracion:read}). Las mutaciones
 * de la geografia viven en los recursos {@code /master/cantones}, {@code /master/parroquias} y
 * {@code /master/jrvs} y exigen {@code configuracion:write} (solo MASTER).
 */
@Path("/api/v1/master/geografia")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MasterGeografiaResource {

  @Inject GeografiaService service;
  @Inject Autorizador autorizador;

  /** Arbol provincia → canton → parroquia → JRV. */
  @GET
  public GeografiaTreeDto get() {
    autorizador.exigir("configuracion:read");
    return service.listarArbol();
  }
}
