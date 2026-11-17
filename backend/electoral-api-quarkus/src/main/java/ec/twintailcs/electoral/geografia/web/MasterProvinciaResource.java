package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import ec.twintailcs.electoral.geografia.application.GeografiaService;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.web.dto.ProvinciaRequest;
import ec.twintailcs.electoral.geografia.web.dto.ProvinciaResponse;
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
 * CRUD de provincias, en {@code /api/v1/master/provincias}.
 *
 * <p>Las tres mutaciones exigen {@code configuracion:write}, que solo MASTER tiene. ADMIN puede
 * leer la geografia ({@code configuracion:read}) pero no escribirla.
 */
@Path("/api/v1/master/provincias")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MasterProvinciaResource {

  @Inject GeografiaService service;
  @Inject Autorizador autorizador;
  @Inject ContextoSolicitud contexto;

  /** Crear provincia. */
  @POST
  public Response crear(
      @Valid ProvinciaRequest dto, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Provincia provincia =
        service.crearProvincia(dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.status(Response.Status.CREATED).entity(toDto(provincia)).build();
  }

  /** Editar provincia. */
  @PUT
  @Path("/{id}")
  public Response actualizar(
      @PathParam("id") Long id,
      @Valid ProvinciaRequest dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Provincia provincia =
        service.actualizarProvincia(id, dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.ok(toDto(provincia)).build();
  }

  /** Eliminar provincia. 409 si todavia tiene cantones. */
  @DELETE
  @Path("/{id}")
  public Response eliminar(
      @PathParam("id") Long id, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    service.eliminarProvincia(id, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.noContent().build();
  }

  private static ProvinciaResponse toDto(Provincia provincia) {
    return new ProvinciaResponse(provincia.getId(), provincia.getNombre(), provincia.getCodigo());
  }
}
