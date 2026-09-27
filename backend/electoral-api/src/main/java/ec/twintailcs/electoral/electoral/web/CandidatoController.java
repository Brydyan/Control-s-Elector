package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.application.ConfiguracionService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoResponse;
import ec.twintailcs.electoral.electoral.web.dto.mapper.CandidatoMapper;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST para la configuracion preelectoral de Candidatos.
 *
 * <p>Rutas: GET /api/v1/configuracion/candidatos?cargoId=&partidoId= # cualquier autenticado,
 * filtros opcionales POST /api/v1/configuracion/candidatos # MASTER PUT
 * /api/v1/configuracion/candidatos/{id} # MASTER DELETE /api/v1/configuracion/candidatos/{id} #
 * MASTER
 */
@Tag(
    name = "Configuracion Electoral",
    description = "Catalogos de cantones, cargos, partidos politicos y candidatos.")
@RestController
@RequestMapping("/api/v1/configuracion/candidatos")
public class CandidatoController {

  private final ConfiguracionService service;
  private final CandidatoMapper mapper;
  private final CurrentUser currentUser;
  private final UsuarioRepository usuarios;

  public CandidatoController(
      ConfiguracionService service,
      CandidatoMapper mapper,
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
      summary = "Listar candidatos",
      description = "Devuelve todos los candidatos, con filtros opcionales por cargo y partido.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Lista de candidatos"),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("isAuthenticated()")
  @GetMapping
  public List<CandidatoResponse> listar(
      @Parameter(description = "Filtrar por ID de cargo") @RequestParam(required = false)
          Long cargoId,
      @Parameter(description = "Filtrar por ID de partido") @RequestParam(required = false)
          Long partidoId) {
    return service.listCandidatos(cargoId, partidoId).stream().map(mapper::toDto).toList();
  }

  @Operation(summary = "Crear candidato", description = "Solo MASTER (configuracion:write).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Candidato creado",
        content = @Content(schema = @Schema(implementation = CandidatoResponse.class))),
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
  public ResponseEntity<CandidatoResponse> crear(
      @Valid @RequestBody CandidatoRequest dto, HttpServletRequest request) {
    Candidato candidato = service.createCandidato(dto, masterId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(candidato));
  }

  @Operation(summary = "Actualizar candidato", description = "Solo MASTER (configuracion:write).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Candidato actualizado",
        content = @Content(schema = @Schema(implementation = CandidatoResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Candidato no encontrado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('configuracion:write')")
  @PutMapping("/{id}")
  public CandidatoResponse actualizar(
      @Parameter(description = "ID del candidato") @PathVariable Long id,
      @Valid @RequestBody CandidatoRequest dto,
      HttpServletRequest request) {
    Candidato candidato = service.updateCandidato(id, dto, masterId(), request);
    return mapper.toDto(candidato);
  }

  @Operation(summary = "Eliminar candidato", description = "Solo MASTER (configuracion:write).")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Candidato eliminado"),
    @ApiResponse(
        responseCode = "404",
        description = "Candidato no encontrado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('configuracion:write')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> eliminar(
      @Parameter(description = "ID del candidato") @PathVariable Long id,
      HttpServletRequest request) {
    service.deleteCandidato(id, masterId(), request);
    return ResponseEntity.noContent().build();
  }
}
