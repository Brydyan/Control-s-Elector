package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.application.ConfiguracionService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import ec.twintailcs.electoral.electoral.web.dto.CargoResponse;
import ec.twintailcs.electoral.electoral.web.dto.mapper.CargoMapper;
import ec.twintailcs.electoral.shared.web.dto.ApiErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST para la configuracion preelectoral de Cargos.
 *
 * <p>Rutas: GET /api/v1/configuracion/cargos # cualquier usuario autenticado POST
 * /api/v1/configuracion/cargos # MASTER PUT /api/v1/configuracion/cargos/{id} # MASTER DELETE
 * /api/v1/configuracion/cargos/{id} # MASTER
 */
@Tag(
    name = "Configuracion Electoral",
    description = "Catalogos de cantones, cargos, partidos politicos y candidatos.")
@RestController
@RequestMapping("/api/v1/configuracion/cargos")
public class CargoController {

  private final ConfiguracionService service;
  private final CargoMapper mapper;
  private final CurrentUser currentUser;
  private final UsuarioRepository usuarios;

  public CargoController(
      ConfiguracionService service,
      CargoMapper mapper,
      CurrentUser currentUser,
      UsuarioRepository usuarios) {
    this.service = service;
    this.mapper = mapper;
    this.currentUser = currentUser;
    this.usuarios = usuarios;
  }

  private Long masterId() {
    return usuarios
        .findByPublicId(currentUser.id())
        .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"))
        .getId();
  }

  @Operation(
      summary = "Listar cargos electorales",
      description =
          "Devuelve todos los cargos (Prefecto, Alcalde, Concejal, etc.) con nivel geografico.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Lista de cargos"),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("isAuthenticated()")
  @GetMapping
  public List<CargoResponse> listar() {
    return service.listCargos().stream().map(mapper::toDto).toList();
  }

  @Operation(summary = "Crear cargo electoral", description = "Solo MASTER (configuracion:write).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Cargo creado",
        content = @Content(schema = @Schema(implementation = CargoResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Datos invalidos",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Permisos insuficientes",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('configuracion:write')")
  @PostMapping
  public ResponseEntity<CargoResponse> crear(
      @Valid @RequestBody CargoRequest dto, HttpServletRequest request) {
    Cargo cargo = service.createCargo(dto, masterId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(cargo));
  }

  @Operation(summary = "Actualizar cargo electoral", description = "Solo MASTER.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Cargo actualizado",
        content = @Content(schema = @Schema(implementation = CargoResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Cargo no encontrado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('configuracion:write')")
  @PutMapping("/{id}")
  public CargoResponse actualizar(
      @Parameter(description = "ID del cargo") @PathVariable Long id,
      @Valid @RequestBody CargoRequest dto,
      HttpServletRequest request) {
    Cargo cargo = service.updateCargo(id, dto, masterId(), request);
    return mapper.toDto(cargo);
  }

  @Operation(summary = "Eliminar cargo electoral", description = "Solo MASTER.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Cargo eliminado"),
    @ApiResponse(
        responseCode = "404",
        description = "Cargo no encontrado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('configuracion:write')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> eliminar(
      @Parameter(description = "ID del cargo") @PathVariable Long id, HttpServletRequest request) {
    service.deleteCargo(id, masterId(), request);
    return ResponseEntity.noContent().build();
  }
}
