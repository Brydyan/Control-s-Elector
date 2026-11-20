package ec.twintailcs.electoral.shared.web;

import ec.twintailcs.electoral.storage.s3.S3Properties;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

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
  @Inject S3Client s3Client;
  @Inject S3Properties s3Properties;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    boolean dbUp = true;
    String dbDetalle = "isValid()";
    try (Connection connection = dataSource.getConnection()) {
      if (!connection.isValid(2)) {
        dbUp = false;
        dbDetalle = "connection not valid";
      }
    } catch (Exception e) {
      dbUp = false;
      dbDetalle = e.getMessage();
    }

    boolean s3Up = true;
    String s3Detalle = "bucket-ok";
    try {
      s3Client.headBucket(
          HeadBucketRequest.builder().bucket(s3Properties.getBucket()).build());
    } catch (Exception e) {
      s3Up = false;
      s3Detalle = e.getMessage();
    }

    Map<String, Object> db = new LinkedHashMap<>();
    db.put("details", Map.of("validationQuery", dbDetalle));
    db.put("status", dbUp ? "UP" : "DOWN");

    Map<String, Object> s3 = new LinkedHashMap<>();
    s3.put("details", Map.of("bucket", s3Detalle));
    s3.put("status", s3Up ? "UP" : "DOWN");

    Map<String, Object> components = new LinkedHashMap<>();
    components.put("db", db);
    components.put("s3", s3);

    boolean allUp = dbUp && s3Up;
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", allUp ? "UP" : "DOWN");
    body.put("components", components);

    return Response.status(allUp ? 200 : 503).type(MediaType.APPLICATION_JSON).entity(body).build();
  }
}
