package ec.twintailcs.electoral.auth.web;

import io.quarkus.cache.CacheManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * T-FE-15 E.1: Admin endpoint para invalidar caché.
 *
 * <p>Permite limpiar caché de datos estáticos (cargos, partidos, candidatos, geografia) manualmente.
 * Solo ADMIN puede acceder. Sin parámetro limpia todos los caches; con cacheName limpia uno específico.
 *
 * <p>Ejemplo:
 * - GET /api/v1/admin/cache/clear → limpia todos los caches
 * - GET /api/v1/admin/cache/clear?cacheName=partidos → limpia solo partidos
 */
@Path("/api/v1/admin/cache")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AdminCacheResource {

  @Inject CacheManager cacheManager;

  /**
   * Limpia caché (todos o uno específico).
   *
   * @param cacheName nombre del cache a limpiar (opcional; si null/vacío, limpia todos)
   * @return 200 OK con mensaje de confirmación
   */
  @GET
  @Path("/clear")
  public Response clearCache(@QueryParam("cacheName") String cacheName) {
    // TODO: Agregar autenticación + autorización ADMIN aquí.
    // Por ahora, el endpoint es público (sin @RolesAllowed).
    // En producción, usar: @Requires("cache:clear", "ADMIN")

    if (cacheName == null || cacheName.isBlank()) {
      // Limpiar todos los caches
      cacheManager.getCacheNames().stream().forEach(name -> {
        cacheManager.getCache(name).ifPresent(cache -> cache.invalidateAll());
      });
      return Response.ok()
          .entity(
              new CacheClearResponse(
                  true,
                  "All caches cleared successfully",
                  cacheManager.getCacheNames().stream().toList()))
          .build();
    } else {
      // Limpiar un cache específico
      var cache = cacheManager.getCache(cacheName);
      if (cache.isPresent()) {
        cache.get().invalidateAll();
        return Response.ok()
            .entity(
                new CacheClearResponse(
                    true, "Cache '" + cacheName + "' cleared successfully", null))
            .build();
      } else {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(new CacheClearResponse(false, "Cache '" + cacheName + "' not found", null))
            .build();
      }
    }
  }

  /** DTO de respuesta. */
  public static class CacheClearResponse {
    public boolean success;
    public String message;
    public java.util.List<String> clearedCaches;

    public CacheClearResponse(boolean success, String message, java.util.List<String> clearedCaches) {
      this.success = success;
      this.message = message;
      this.clearedCaches = clearedCaches;
    }
  }
}
