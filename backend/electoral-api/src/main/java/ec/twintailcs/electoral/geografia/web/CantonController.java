package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.geografia.domain.CantonRepository;
import ec.twintailcs.electoral.geografia.web.dto.CantonResponse;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST para la lectura de cantones.
 *
 * <p>Ruta: GET /api/v1/configuracion/cantones # cualquier usuario autenticado
 *
 * <p>Solo expone lectura. La creación/edición de cantones no forma parte de la bandeja del
 * coordinador y queda fuera del alcance de este endpoint.
 */
@RestController
@RequestMapping("/api/v1/configuracion/cantones")
public class CantonController {

  private final CantonRepository cantones;

  public CantonController(CantonRepository cantones) {
    this.cantones = cantones;
  }

  @PreAuthorize("isAuthenticated()")
  @GetMapping
  public List<CantonResponse> listar() {
    return cantones.findAll(Sort.by("nombre").ascending()).stream()
        .map(c -> new CantonResponse(c.getId(), c.getNombre(), c.getCodigo()))
        .toList();
  }
}
