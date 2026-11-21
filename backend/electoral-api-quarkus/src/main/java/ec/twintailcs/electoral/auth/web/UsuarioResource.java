package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.UsuarioAdminService;
import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.auth.web.dto.CreateUsuarioRequest;
import ec.twintailcs.electoral.auth.web.dto.UpdateUsuarioRequest;
import ec.twintailcs.electoral.auth.web.dto.UsuarioResponseDto;
import ec.twintailcs.electoral.common.web.RateLimited;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * Administracion de usuarios. Solo MASTER (o los permisos indicados).
 *
 * <p>Port exacto del {@code UsuarioController} del backend Spring: mismos paths, mismos permisos,
 * mismos status y mismo cuerpo.
 *
 * <p>El recurso es un adaptador thin: arma la peticion y devuelve la respuesta. El requisito de
 * permisos de cada operacion vive en el servicio ({@link UsuarioAdminService}), que es donde el
 * interceptor CDI que evalua {@code @Requiere} puede correr: Quarkus REST invoca los metodos del
 * recurso directamente y no les aplica interceptores.
 */
@Path("/api/v1/admin/usuarios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class UsuarioResource {

  @Inject Autorizador autorizador;
  @Inject UsuarioAdminService service;

  /** Listar usuarios, con filtros opcionales. */
  @GET
  @RateLimited("admin-usuarios")
  public PagedResponse<UsuarioResponseDto> listar(
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("size") @DefaultValue("20") int size,
      @QueryParam("sort") @DefaultValue("creadoEn,desc") String sort,
      @QueryParam("rol") String rol,
      @QueryParam("habilitado") Boolean habilitado) {
    autorizador.exigir("usuario:read", "MASTER");

    return service.listar(page, size, sort, rol, habilitado);
  }

  /** Obtener usuario por su UUID publico. */
  @GET
  @Path("/{publicId}")
  public UsuarioResponseDto obtener(@PathParam("publicId") UUID publicId) {
    autorizador.exigir("usuario:read", "MASTER");

    return service.obtener(publicId);
  }

  /** Crear usuario con password temporal autogenerado. El email se genera solo. */
  @POST
  public Response crear(@Valid CreateUsuarioRequest request) {
    autorizador.exigir("usuario:create", "MASTER");
    return Response.status(Response.Status.CREATED).entity(service.crear(request)).build();
  }

  /** Actualizar nombre, apellido, rol y estado de habilitacion. */
  @PUT
  @Path("/{publicId}")
  public UsuarioResponseDto actualizar(
      @PathParam("publicId") UUID publicId, @Valid UpdateUsuarioRequest request) {
    autorizador.exigir("usuario:update", "MASTER");

    return service.actualizar(publicId, request);
  }

  /** Deshabilitar usuario (soft-delete). */
  @DELETE
  @Path("/{publicId}")
  public Response deshabilitar(@PathParam("publicId") UUID publicId) {
    autorizador.exigir("usuario:delete", "MASTER");

    service.deshabilitar(publicId);
    return Response.noContent().build();
  }
}
