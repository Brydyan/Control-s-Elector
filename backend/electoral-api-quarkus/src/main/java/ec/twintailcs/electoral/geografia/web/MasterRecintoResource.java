package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.geografia.application.RecintoService;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.domain.Recinto;
import ec.twintailcs.electoral.geografia.web.dto.RecintoRequest;
import ec.twintailcs.electoral.geografia.web.dto.RecintoResponse;
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

/**
 * CRUD de Recintos en {@code /api/v1/admin/recintos}.
 *
 * <p>Todas las operaciones exigen {@code configuracion:write}, que solo MASTER tiene.
 * Recintos agrupa múltiples JRVs bajo un COORDINATOR responsable de validar actas.
 */
@Path("/api/v1/admin/recintos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MasterRecintoResource {

  @Inject RecintoService service;
  @Inject Autorizador autorizador;

  /**
   * Listar todos los Recintos con paginación.
   *
   * @param page Página (0-indexed)
   * @param size Tamaño de página
   * @return Lista paginada de Recintos
   */
  @GET
  public Response listar(
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    autorizador.exigir("configuracion:read");

    PagedResponse<Recinto> paginada = service.listarRecintos(page, size);
    return Response.ok(toPagedResponse(paginada)).build();
  }

  /**
   * Obtener un Recinto por id.
   *
   * @param id ID del Recinto
   * @return Recinto si existe
   */
  @GET
  @Path("/{id}")
  public Response obtener(@PathParam("id") Long id) {
    autorizador.exigir("configuracion:read");

    Recinto recinto = service.obtenerRecinto(id);
    return Response.ok(toDto(recinto)).build();
  }

  /**
   * Crear un nuevo Recinto.
   *
   * @param dto Request con nombre, parroquiaId, coordinatorId, estado
   * @return Recinto creado (201 Created)
   */
  @POST
  public Response crear(@Valid RecintoRequest dto) {
    autorizador.exigir("configuracion:write");

    Recinto recinto =
        service.crearRecinto(
            dto.nombre(), dto.parroquiaId(), dto.coordinatorId(), dto.estado());
    return Response.status(Response.Status.CREATED).entity(toDto(recinto)).build();
  }

  /**
   * Actualizar un Recinto.
   *
   * @param id ID del Recinto
   * @param dto Request con campos opcionales a actualizar
   * @return Recinto actualizado
   */
  @PUT
  @Path("/{id}")
  public Response actualizar(@PathParam("id") Long id, @Valid RecintoRequest dto) {
    autorizador.exigir("configuracion:write");

    Recinto recinto =
        service.actualizarRecinto(id, dto.nombre(), dto.coordinatorId(), dto.estado());
    return Response.ok(toDto(recinto)).build();
  }

  /**
   * Eliminar un Recinto.
   *
   * @param id ID del Recinto
   * @return 204 No Content si exitoso, 409 si tiene JRVs asignadas
   */
  @DELETE
  @Path("/{id}")
  public Response eliminar(@PathParam("id") Long id) {
    autorizador.exigir("configuracion:write");

    service.eliminarRecinto(id);
    return Response.noContent().build();
  }

  // --- Mappers ---

  private RecintoResponse toDto(Recinto recinto) {
    Parroquia parroquia = Parroquia.findById(recinto.getParroquiaId());
    String parroquiaNombre = parroquia != null ? parroquia.getNombre() : "UNKNOWN";

    Usuario coordinator = null;
    RecintoResponse.CoordinadorInfo coordinadorInfo = null;
    if (recinto.getCoordinatorId() != null) {
      coordinator = Usuario.findById(recinto.getCoordinatorId());
      if (coordinator != null) {
        coordinadorInfo =
            new RecintoResponse.CoordinadorInfo(
                coordinator.getId(), coordinator.getNombre(), coordinator.getEmail());
      }
    }

    // Calculate total mesas (count JRVs in Recinto)
    // For now, we'll leave it null/0 as it requires a service call
    // In production, this should be optimized with a query that includes the count
    Integer totalMesas = 0; // TODO: Calculate from JRV count

    return new RecintoResponse(
        recinto.getId(),
        recinto.getNombre(),
        parroquiaNombre,
        coordinadorInfo,
        totalMesas,
        recinto.getEstado(),
        recinto.getFechaCreacion());
  }

  private PagedResponse<RecintoResponse> toPagedResponse(PagedResponse<Recinto> paginada) {
    return PagedResponse.of(
        paginada.data().stream().map(this::toDto).toList(),
        paginada.meta().page(),
        paginada.meta().size(),
        paginada.meta().totalElements());
  }
}
