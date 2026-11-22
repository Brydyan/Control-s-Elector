package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import io.quarkus.cache.CacheManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * T-CACHE-STRAT-01 E.1: endpoint de administracion para invalidar la cache de datos estaticos.
 *
 * <p>Ejemplos:
 *
 * <ul>
 *   <li>{@code GET /api/v1/admin/cache/clear} → limpia todos los caches
 *   <li>{@code GET /api/v1/admin/cache/clear?cacheName=partidos} → limpia solo {@code partidos}
 * </ul>
 *
 * <p><b>Matriz de roles.</b> Exige el permiso {@code cache:clear} o el rol {@code MASTER}. No es
 * una operacion de ADMIN: ADMIN no escribe en este dominio, y vaciar la cache es una escritura
 * sobre el estado operativo de la aplicacion. La autorizacion va con {@link
 * Autorizador#exigir(String...)} —el patron del repo— y no con {@code @RolesAllowed}, que no se usa
 * en ninguna parte del backend.
 *
 * <p><b>Por que el permiso nuevo no necesita fila en {@code permiso}.</b> {@code Autorizador}
 * compara contra el conjunto efectivo del usuario, que es su rol mas los permisos atomicos del
 * claim {@code perms}: {@code MASTER} entra por su rol, y cualquier otro rol necesitaria una fila
 * en {@code permiso} con {@code (recurso='cache', accion='clear')}, que se puede agregar en
 * Liquibase si algun dia se delega la operacion.
 *
 * <p><b>Auditoria.</b> Todo vaciado queda registrado en {@code audit_log} por el camino generico
 * ({@link AuditLogService#record}), que es el unico que escribe esa tabla: dejar el rastro sin el,
 * o duplicarlo con otro servicio, es como el rastro se desincroniza del estado real.
 */
@Path("/api/v1/admin/cache")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AdminCacheResource {

  @Inject CacheManager cacheManager;
  @Inject Autorizador autorizador;
  @Inject ContextoSolicitud contexto;
  @Inject AuditLogService audit;

  /**
   * Limpia la cache: todos los caches, o uno solo.
   *
   * @param cacheName nombre del cache a limpiar; si es null o vacio, limpia todos
   * @param xForwardedFor IP del cliente, la que deja el proxy
   * @return 200 con el detalle de lo limpiado, 404 si el cache pedido no existe
   */
  @GET
  @Path("/clear")
  public Response clearCache(
      @QueryParam("cacheName") String cacheName,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("cache:clear", "MASTER");

    String ip = contexto.ip(xForwardedFor);

    if (cacheName == null || cacheName.isBlank()) {
      List<String> nombres = cacheManager.getCacheNames().stream().sorted().toList();
      for (String nombre : nombres) {
        cacheManager
            .getCache(nombre)
            .ifPresent(cache -> cache.invalidateAll().await().indefinitely());
      }
      audit.record(
          "CACHE",
          0L,
          contexto.usuarioId(),
          "LIMPIAR_CACHE",
          Map.of("cacheName", "*", "cachesCleared", nombres),
          ip);
      return Response.ok(new CacheClearResponse(true, "All caches cleared successfully", nombres))
          .build();
    }

    var cache = cacheManager.getCache(cacheName);
    if (cache.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new CacheClearResponse(false, "Cache '" + cacheName + "' not found", List.of()))
          .build();
    }

    cache.get().invalidateAll().await().indefinitely();
    audit.record(
        "CACHE",
        0L,
        contexto.usuarioId(),
        "LIMPIAR_CACHE",
        Map.of("cacheName", cacheName, "cachesCleared", List.of(cacheName)),
        ip);
    return Response.ok(
            new CacheClearResponse(
                true, "Cache '" + cacheName + "' cleared successfully", List.of(cacheName)))
        .build();
  }

  /**
   * Respuesta del vaciado.
   *
   * @param success si el vaciado ocurrio
   * @param message detalle legible
   * @param clearedCaches nombres de los caches limpiados
   */
  public record CacheClearResponse(boolean success, String message, List<String> clearedCaches) {}
}
