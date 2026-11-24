package ec.twintailcs.electoral.shared.web;

import io.quarkus.runtime.LaunchMode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Métricas en {@code /q/metrics}, en el puerto principal.
 *
 * <p>Quarkus mueve el export de Prometheus al management interface cuando este está habilitado
 * ({@code quarkus.management.enabled=true}), y ese puerto no se publica en Railway: el único puerto
 * exposed es el principal. Sin este alias, {@code /q/metrics} —la ruta que el SDD de caché declara
 * como pública y scrapeable (D.5), y la que documentan {@code CACHING_STRATEGY.md} y {@code
 * CACHE_SETUP_GUIDE.md}— desaparecería del puerto principal y el tablero de métricas quedaría a
 * ciegas.
 *
 * <p>El alias no reimplementa nada: lee el management interface por HTTP y devuelve su cuerpo y su
 * content-type tal cual. El management interface sigue siendo el dueño real de las métricas; este
 * endpoint solo las publica donde el scraper las puede alcanzar. Es el mismo patrón que {@link
 * HealthAliasResource} usa para el health.
 *
 * <p>La ruta es pública a propósito ({@code JwtAuthFilter.PUBLICAS} incluye {@code "/q"}): un
 * scraper no puede autenticar.
 */
@Path("/q/metrics")
public class MetricsAliasResource {

  /** Puerto del management interface en producción. */
  @ConfigProperty(name = "quarkus.management.port", defaultValue = "9000")
  int managementPort;

  /**
   * Puerto del management interface en modo test: Quarkus ignora {@code quarkus.management.port}
   * cuando corre en TEST y usa {@code quarkus.management.test-port} (9001 por defecto).
   */
  @ConfigProperty(name = "quarkus.management.test-port", defaultValue = "9001")
  int managementTestPort;

  /** Root path del management interface ({@code /actuator} en este proyecto). */
  @ConfigProperty(name = "quarkus.management.root-path", defaultValue = "/")
  String managementRootPath;

  private volatile HttpClient httpClient;

  /**
   * Devuelve el export de métricas del management interface con el status y el content-type que
   * trae upstream.
   */
  @GET
  public Response metrics() {
    for (String path : rutasDelUpstream()) {
      try {
        HttpResponse<String> r =
            cliente()
                .send(
                    HttpRequest.newBuilder(URI.create(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 200) {
          String contentType = r.headers().firstValue("content-type").orElse(MediaType.TEXT_PLAIN);
          return Response.ok(r.body(), contentType).build();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return error("Interrumpido al leer las metricas del management interface");
      } catch (Exception e) {
        return error("No se pudo leer el management interface de metricas");
      }
    }
    return error("El management interface no expone el endpoint de metricas");
  }

  /**
   * Rutas candidatas del export de métricas dentro del management interface.
   *
   * <p>Con {@code quarkus.management.root-path=/actuator} el export vive en {@code
   * /actuator/metrics}; la segunda ruta cubre el nombre alternativo cuando el root path es {@code
   * /}. Se prueban en orden y gana la primera que responda 200.
   */
  private String[] rutasDelUpstream() {
    String base = "http://127.0.0.1:" + puertoEfectivo() + rootPathNormalizado();
    return new String[] {base + "/metrics", base + "/q/metrics"};
  }

  /**
   * Puerto donde el management interface está escuchando de verdad.
   *
   * <p>En TEST, Quarkus usa {@code quarkus.management.test-port} y deja de mirar {@code
   * quarkus.management.port}: sin esta distinción el alias apuntaría a un puerto donde no hay nadie
   * escuchando y las métricas devolverían 503.
   */
  private int puertoEfectivo() {
    return LaunchMode.current() == LaunchMode.TEST ? managementTestPort : managementPort;
  }

  /** Root path con barra inicial y sin barra final. */
  private String rootPathNormalizado() {
    String path = managementRootPath == null ? "" : managementRootPath.trim();
    if (path.isEmpty() || path.equals("/")) {
      return "";
    }
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  private HttpClient cliente() {
    HttpClient actual = httpClient;
    if (actual == null) {
      synchronized (this) {
        actual = httpClient;
        if (actual == null) {
          actual = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
          httpClient = actual;
        }
      }
    }
    return actual;
  }

  private Response error(String mensaje) {
    return Response.status(503).type(MediaType.TEXT_PLAIN).entity(mensaje).build();
  }
}
