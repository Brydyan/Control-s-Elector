package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import ec.twintailcs.electoral.geografia.application.GeografiaService;
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.web.dto.CantonRequest;
import ec.twintailcs.electoral.geografia.web.dto.CantonResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * CRUD de cantones, en {@code /api/v1/master/cantones}.
 *
 * <p>Las tres mutaciones exigen {@code configuracion:write}, que solo MASTER tiene. ADMIN puede
 * leer la geografia ({@code configuracion:read}) pero no escribirla.
 */
@Path("/api/v1/master/cantones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MasterCantonResource {

  @Inject GeografiaService service;
  @Inject Autorizador autorizador;
  @Inject ContextoSolicitud contexto;

  /** Crear canton bajo una provincia. */
  @POST
  public Response crear(
      @Valid CantonRequest dto, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Canton canton = service.crearCanton(dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.status(Response.Status.CREATED).entity(toDto(canton)).build();
  }

  /** Editar canton. */
  @PUT
  @Path("/{id}")
  public Response actualizar(
      @PathParam("id") Long id,
      @Valid CantonRequest dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Canton canton =
        service.actualizarCanton(id, dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.ok(toDto(canton)).build();
  }

  /** Eliminar canton. 409 si todavia tiene parroquias. */
  @DELETE
  @Path("/{id}")
  public Response eliminar(
      @PathParam("id") Long id, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    service.eliminarCanton(id, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.noContent().build();
  }

  private static CantonResponse toDto(Canton canton) {
    return new CantonResponse(
        canton.getId(), canton.getNombre(), canton.getCodigo(), canton.getProvinciaId());
  }
}
