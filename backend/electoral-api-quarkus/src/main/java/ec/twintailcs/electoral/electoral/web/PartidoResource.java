package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.common.web.RateLimited;
import ec.twintailcs.electoral.electoral.application.ConfiguracionService;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import ec.twintailcs.electoral.electoral.web.dto.PartidoResponse;
import ec.twintailcs.electoral.electoral.web.dto.mapper.PartidoMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Endpoints REST para la configuracion preelectoral de Partidos.
 *
 * <p>Port exacto del {@code PartidoController} del backend Spring: mismos paths, mismos permisos,
 * mismos status y mismo cuerpo.
 *
 * <p>Los listados son de lectura abierta a cualquier usuario autenticado (el filtro de JWT ya exige
 * token); las escrituras exigen {@code configuracion:write}, que solo tiene MASTER.
 */
@Path("/api/v1/configuracion/partidos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PartidoResource {

  @Inject ConfiguracionService service;
  @Inject PartidoMapper mapper;
  @Inject Autorizador autorizador;
  @Inject ContextoSolicitud contexto;

  /** Listar partidos politicos. */
  @GET
  @RateLimited("config-read")
  public List<PartidoResponse> listar() {
    return service.listPartidos().stream().map(mapper::toDto).toList();
  }

  /** Crear partido politico. Solo MASTER. */
  @POST
  public Response crear(
      @Valid PartidoRequest dto, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Partido partido = service.createPartido(dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.status(Response.Status.CREATED).entity(mapper.toDto(partido)).build();
  }

  /** Actualizar partido politico. Solo MASTER. */
  @PUT
  @Path("/{id}")
  public PartidoResponse actualizar(
      @PathParam("id") Long id,
      @Valid PartidoRequest dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    return mapper.toDto(
        service.updatePartido(id, dto, contexto.usuarioId(), contexto.ip(xForwardedFor)));
  }

  /** Eliminar partido politico. Solo MASTER. */
  @DELETE
  @Path("/{id}")
  public Response eliminar(
      @PathParam("id") Long id, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    service.deletePartido(id, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.noContent().build();
  }
}
