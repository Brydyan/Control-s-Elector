package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.electoral.application.ConfiguracionService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import ec.twintailcs.electoral.electoral.web.dto.CargoResponse;
import ec.twintailcs.electoral.electoral.web.dto.mapper.CargoMapper;
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
 * Endpoints REST para la configuracion preelectoral de Cargos.
 *
 * <p>Port exacto del {@code CargoController} del backend Spring: mismos paths, mismos permisos,
 * mismos status y mismo cuerpo.
 */
@Path("/api/v1/configuracion/cargos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CargoResource {

  @Inject ConfiguracionService service;
  @Inject CargoMapper mapper;
  @Inject Autorizador autorizador;
  @Inject ContextoSolicitud contexto;

  /** Listar cargos electorales. */
  @GET
  public List<CargoResponse> listar() {
    return service.listCargos().stream().map(mapper::toDto).toList();
  }

  /** Crear cargo electoral. Solo MASTER (configuracion:write). */
  @POST
  public Response crear(
      @Valid CargoRequest dto, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    Cargo cargo = service.createCargo(dto, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.status(Response.Status.CREATED).entity(mapper.toDto(cargo)).build();
  }

  /** Actualizar cargo electoral. Solo MASTER. */
  @PUT
  @Path("/{id}")
  public CargoResponse actualizar(
      @PathParam("id") Long id,
      @Valid CargoRequest dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    return mapper.toDto(
        service.updateCargo(id, dto, contexto.usuarioId(), contexto.ip(xForwardedFor)));
  }

  /** Eliminar cargo electoral. Solo MASTER. */
  @DELETE
  @Path("/{id}")
  public Response eliminar(
      @PathParam("id") Long id, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("configuracion:write");

    service.deleteCargo(id, contexto.usuarioId(), contexto.ip(xForwardedFor));
    return Response.noContent().build();
  }
}
