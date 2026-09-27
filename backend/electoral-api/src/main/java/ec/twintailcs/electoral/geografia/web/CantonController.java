package ec.twintailcs.electoral.geografia.web;

import ec.twintailcs.electoral.geografia.domain.CantonRepository;
import ec.twintailcs.electoral.geografia.web.dto.CantonResponse;
import ec.twintailcs.electoral.shared.web.dto.ApiErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
    name = "Configuracion Electoral",
    description = "Catalogos de cantones, cargos, partidos politicos y candidatos.")
@RestController
@RequestMapping("/api/v1/configuracion/cantones")
public class CantonController {

  private final CantonRepository cantones;

  public CantonController(CantonRepository cantones) {
    this.cantones = cantones;
  }

  @Operation(
      summary = "Listar cantones",
      description =
          "Devuelve todos los cantones de la Provincia de Santa Elena ordenados por nombre.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Lista de cantones"),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("isAuthenticated()")
  @GetMapping
  public List<CantonResponse> listar() {
    return cantones.findAll(Sort.by("nombre").ascending()).stream()
        .map(c -> new CantonResponse(c.getId(), c.getNombre(), c.getCodigo()))
        .toList();
  }
}
