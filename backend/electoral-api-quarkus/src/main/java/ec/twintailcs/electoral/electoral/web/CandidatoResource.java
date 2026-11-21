package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.common.web.RateLimited;
import ec.twintailcs.electoral.electoral.application.ConfiguracionService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoResponse;
import ec.twintailcs.electoral.electoral.web.dto.mapper.CandidatoMapper;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Endpoints REST para la configuracion preelectoral de Candidatos.
 *
 * <p>Port exacto del {@code CandidatoController} del backend Spring: mismos paths, mismos permisos,
 * mismos status y mismo cuerpo.
 */
@Path("/api/v1/configuracion/candidatos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CandidatoResource {

  @Inject ConfiguracionService service;
  @Inject CandidatoMapper mapper;
  @Inject Autorizador autorizador;
  @Inject ContextoSolicitud contexto;

  /** Listar candidatos, con filtros opcionales por cargo y por partido. */
  @GET
  @RateLimited("config-read")
  public List<CandidatoResponse> listar(
      @QueryParam("cargoId") Long cargoId, @QueryParam("partidoId") Long partidoId) {
    return service.listCandidatos(cargoId, partidoId).stream().map(mapper::toDto).toList();
  }

  /** Crear candidato. Solo MASTER (configuracion:write). */
  @POST
  public Response crear(
      @Valid CandidatoRequest dto, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Candidato candidato =
        service.createCandidato(dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.status(Response.Status.CREATED).entity(mapper.toDto(candidato)).build();
  }

  /** Actualizar candidato. Solo MASTER (configuracion:write). */
  @PUT
  @Path("/{id}")
  public CandidatoResponse actualizar(
      @PathParam("id") Long id,
      @Valid CandidatoRequest dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    return mapper.toDto(
        service.updateCandidato(id, dto, contexto.usuarioId(), contexto.ip(xForwardedFor)));
  }

  /** Eliminar candidato. Solo MASTER (configuracion:write). */
  @DELETE
  @Path("/{id}")
  public Response eliminar(
      @PathParam("id") Long id, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    service.deleteCandidato(id, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.noContent().build();
  }
}
