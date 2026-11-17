package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import ec.twintailcs.electoral.geografia.application.GeografiaService;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.web.dto.ParroquiaRequest;
import ec.twintailcs.electoral.geografia.web.dto.ParroquiaResponse;
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
 * CRUD de parroquias, en {@code /api/v1/master/parroquias}.
 *
 * <p>Las tres mutaciones exigen {@code configuracion:write}, que solo MASTER tiene.
 */
@Path("/api/v1/master/parroquias")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MasterParroquiaResource {

  @Inject GeografiaService service;
  @Inject Autorizador autorizador;
  @Inject ContextoSolicitud contexto;

  /** Crear parroquia bajo un canton. */
  @POST
  public Response crear(
      @Valid ParroquiaRequest dto, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Parroquia parroquia =
        service.crearParroquia(dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.status(Response.Status.CREATED).entity(toDto(parroquia)).build();
  }

  /** Editar parroquia. */
  @PUT
  @Path("/{id}")
  public Response actualizar(
      @PathParam("id") Long id,
      @Valid ParroquiaRequest dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Parroquia parroquia =
        service.actualizarParroquia(id, dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.ok(toDto(parroquia)).build();
  }

  /** Eliminar parroquia. 409 si todavia tiene JRVs. */
  @DELETE
  @Path("/{id}")
  public Response eliminar(
      @PathParam("id") Long id, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    service.eliminarParroquia(id, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.noContent().build();
  }

  private static ParroquiaResponse toDto(Parroquia parroquia) {
    return new ParroquiaResponse(
        parroquia.getId(), parroquia.getNombre(), parroquia.isEsUrbana(), parroquia.getCantonId());
  }
}
