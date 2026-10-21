package ec.twintailcs.electoral.shared.web;

import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check en {@code /actuator/health}, en el puerto principal.
 *
 * <p>Quarkus sirve el management interface en un puerto propio (9000 por defecto) y NO monta sus
 * rutas en el servidor principal aunque se le configure el mismo numero de puerto. Como el backend
 * Spring sirve el health en {@code /actuator/health} del puerto principal, este endpoint deja las
 * dos implementaciones intercambiables sin tocar el probe de Kubernetes ni el proxy del strangler.
 *
 * <p>El body tiene la misma forma que el del backend Spring: {@code status} y {@code
 * components.db.status}.
 */
@Path("/actuator/health")
public class HealthAliasResource {

  @Inject AgroalDataSource dataSource;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    boolean dbUp = true;
    String detalle = "isValid()";
    try (Connection connection = dataSource.getConnection()) {
      if (!connection.isValid(2)) {
        dbUp = false;
        detalle = "connection not valid";
      }
    } catch (Exception e) {
      dbUp = false;
      detalle = e.getMessage();
    }

    Map<String, Object> db = new LinkedHashMap<>();
    db.put("details", Map.of("validationQuery", detalle));
    db.put("status", dbUp ? "UP" : "DOWN");

    Map<String, Object> components = new LinkedHashMap<>();
    components.put("db", db);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", dbUp ? "UP" : "DOWN");
    body.put("components", components);

    return Response.status(dbUp ? 200 : 503).type(MediaType.APPLICATION_JSON).entity(body).build();
  }
}
