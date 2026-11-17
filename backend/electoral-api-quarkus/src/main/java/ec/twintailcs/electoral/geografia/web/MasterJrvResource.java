package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import ec.twintailcs.electoral.geografia.application.GeografiaService;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.web.dto.JrvRequest;
import ec.twintailcs.electoral.geografia.web.dto.JrvResponse;
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
 * CRUD de JRVs, en {@code /api/v1/master/jrvs}.
 *
 * <p>Las tres mutaciones exigen {@code configuracion:write}, que solo MASTER tiene. Una JRV con
 * actas registradas no se puede borrar: el registro electoral no puede quedar apuntando a una JRV
 * que ya no existe.
 */
@Path("/api/v1/master/jrvs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MasterJrvResource {

  @Inject GeografiaService service;
  @Inject Autorizador autorizador;
  @Inject ContextoSolicitud contexto;

  /** Crear JRV bajo una parroquia. */
  @POST
  public Response crear(
      @Valid JrvRequest dto, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Jrv jrv = service.crearJrv(dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.status(Response.Status.CREATED).entity(toDto(jrv)).build();
  }

  /** Editar JRV. */
  @PUT
  @Path("/{id}")
  public Response actualizar(
      @PathParam("id") Long id,
      @Valid JrvRequest dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Jrv jrv = service.actualizarJrv(id, dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.ok(toDto(jrv)).build();
  }

  /** Eliminar JRV. 409 si tiene actas registradas. */
  @DELETE
  @Path("/{id}")
  public Response eliminar(
      @PathParam("id") Long id, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    service.eliminarJrv(id, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.noContent().build();
  }

  private static JrvResponse toDto(Jrv jrv) {
    return new JrvResponse(
        jrv.getId(),
        jrv.getNumero(),
        jrv.getRecinto(),
        jrv.getTotalElectores(),
        jrv.getParroquiaId());
  }
}
