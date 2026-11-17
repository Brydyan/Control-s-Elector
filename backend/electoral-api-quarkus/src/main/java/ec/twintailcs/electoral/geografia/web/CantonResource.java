package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.web.dto.CantonResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Endpoints REST para la lectura de cantones.
 *
 * <p>Port exacto del {@code CantonController} del backend Spring: mismo path, mismo status y mismo
 * cuerpo. El listado viene ordenado por nombre, igual que alla.
 *
 * <p>Solo expone lectura. La creacion y edicion de cantones no forma parte de la bandeja del
 * coordinador y queda fuera del alcance de este endpoint.
 */
@Path("/api/v1/configuracion/cantones")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CantonResource {

  /** Listar cantones de la provincia, ordenados por nombre. */
  @GET
  public List<CantonResponse> listar() {
    return Canton.getEntityManager()
        .createQuery("select c from Canton c order by c.nombre asc", Canton.class)
        .getResultList()
        .stream()
        .map(
            canton ->
                new CantonResponse(
                    canton.getId(),
                    canton.getNombre(),
                    canton.getCodigo(),
                    canton.getProvinciaId()))
        .toList();
  }
}
