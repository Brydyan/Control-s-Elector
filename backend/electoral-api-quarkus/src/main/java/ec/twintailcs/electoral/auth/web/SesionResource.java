package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.SesionAdminService;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import ec.twintailcs.electoral.auth.web.dto.SesionResponseDto;
import jakarta.enterprise.context.ApplicationScoped;
import ec.twintailcs.electoral.auth.security.Autorizador;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Administracion de sesiones activas. Solo MASTER (o los permisos indicados).
 *
 * <p>Port exacto del {@code SesionController} del backend Spring: mismos paths, mismos permisos,
 * mismos status y mismo cuerpo.
 *
 * <p>El recurso es un adaptador thin: el requisito de permisos de cada operacion vive en el
 * servicio ({@link SesionAdminService}). Ver {@link UsuarioResource} para el por que.
 */
@Path("/api/v1/admin/sesiones")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SesionResource {

  @Inject Autorizador autorizador;
  @Inject SesionAdminService service;

  /** Listar sesiones activas (no revocadas y no expiradas). */
  @GET
  public PagedResponse<SesionResponseDto> listar(
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("size") @DefaultValue("20") int size,
      @QueryParam("sort") @DefaultValue("ultimoUsoEn,desc") String sort,
      @QueryParam("usuarioId") Long usuarioId) {
    autorizador.exigir("sesion:list", "MASTER");

    return service.listar(page, size, sort, usuarioId);
  }

  /** Obtener el detalle de una sesion activa. */
  @GET
  @Path("/{sesionId}")
  public SesionResponseDto obtener(@PathParam("sesionId") String sesionId) {
    autorizador.exigir("sesion:list", "MASTER");

    return service.obtener(sesionId);
  }

  /** Revocar una sesion. Idempotente si ya estaba revocada. */
  @DELETE
  @Path("/{sesionId}")
  public Response revocar(@PathParam("sesionId") String sesionId) {
    autorizador.exigir("sesion:revoke", "MASTER");

    service.revocar(sesionId);
    return Response.noContent().build();
  }
}
