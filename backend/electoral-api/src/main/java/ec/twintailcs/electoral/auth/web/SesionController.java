package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.SesionRepository;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.auth.web.dto.SesionResponseDto;
import ec.twintailcs.electoral.auth.web.dto.mapper.UsuarioMapper;
import ec.twintailcs.electoral.shared.web.dto.ApiErrorResponseDto;
import ec.twintailcs.electoral.shared.web.pagination.PageRequestFactory;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(
    name = "Administracion",
    description = "Gestion de usuarios y revocacion de sesiones concurrentes (MASTER).")
@RestController
@RequestMapping("/api/v1/admin/sesiones")
public class SesionController {

  private static final Set<String> SORTABLE_FIELDS = Set.of("ultimoUsoEn", "creadoEn", "expiraEn");
  private final SesionRepository sesiones;
  private final UsuarioRepository usuarios;
  private final UsuarioMapper mapper;

  public SesionController(
      SesionRepository sesiones, UsuarioRepository usuarios, UsuarioMapper mapper) {
    this.sesiones = sesiones;
    this.usuarios = usuarios;
    this.mapper = mapper;
  }

  @Operation(
      summary = "Listar sesiones activas",
      description =
          "Devuelve sesiones activas (no revocadas y no expiradas) con paginacion. Solo MASTER.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Pagina de sesiones"),
    @ApiResponse(
        responseCode = "403",
        description = "Permisos insuficientes",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @GetMapping
  @PreAuthorize("hasAuthority('sesion:list') or hasRole('MASTER')")
  public PagedResponse<SesionResponseDto> listar(
      @Parameter(description = "Numero de pagina (0-based)") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Elementos por pagina (max 100)") @RequestParam(defaultValue = "20")
          int size,
      @Parameter(description = "Campo y direccion: ultimoUsoEn,desc")
          @RequestParam(defaultValue = "ultimoUsoEn,desc")
          String sort,
      @Parameter(description = "Filtrar por id interno de usuario") @RequestParam(required = false)
          Long usuarioId) {
    Pageable pageable = pageable(page, size, sort, "ultimoUsoEn");
    Instant now = Instant.now();
    Page<Sesion> result =
        usuarioId == null
            ? sesiones.findByRevocadoFalseAndExpiraEnAfter(now, pageable)
            : sesiones.findByUsuarioIdAndRevocadoFalseAndExpiraEnAfter(usuarioId, now, pageable);
    return PagedResponse.of(result.map(this::toDto));
  }

  @Operation(
      summary = "Obtener sesion por ID",
      description = "Retorna el detalle de una sesion activa especifica. Solo MASTER.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Sesion encontrada",
        content = @Content(schema = @Schema(implementation = SesionResponseDto.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Permisos insuficientes",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Sesion no encontrada o inactiva",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @GetMapping("/{sesionId}")
  @PreAuthorize("hasAuthority('sesion:list') or hasRole('MASTER')")
  public SesionResponseDto obtener(
      @Parameter(description = "UUID publico de la sesion") @PathVariable String sesionId) {
    Sesion sesion = findSession(sesionId);
    return toDto(sesion);
  }

  @Operation(
      summary = "Revocar sesion",
      description =
          "Invalida el refresh token de la sesion indicada. Idempotente si ya estaba revocada. Solo MASTER.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Sesion revocada"),
    @ApiResponse(
        responseCode = "403",
        description = "Permisos insuficientes",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Sesion no encontrada o ya inactiva",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @DeleteMapping("/{sesionId}")
  @PreAuthorize("hasAuthority('sesion:revoke') or hasRole('MASTER')")
  public ResponseEntity<Void> revocar(
      @Parameter(description = "UUID publico de la sesion") @PathVariable String sesionId) {
    Sesion sesion = findSession(sesionId);
    if (sesion.isRevocado()) {
      throw notFound();
    }
    sesion.setRevocado(true);
    sesiones.save(sesion);
    return ResponseEntity.noContent().build();
  }

  private Sesion findSession(String sesionId) {
    Sesion sesion = sesiones.findBySesionId(sesionId).orElseThrow(() -> notFound());
    if (!sesion.activa()) {
      throw notFound();
    }
    return sesion;
  }

  private SesionResponseDto toDto(Sesion sesion) {
    String email = usuarios.findById(sesion.getUsuarioId()).map(u -> u.getEmail()).orElse(null);
    return mapper.toSesionDto(sesion, email);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesion no encontrada");
  }

  private static Pageable pageable(int page, int size, String sort, String defaultField) {
    String[] parts = sort.split(",", 2);
    String direction = parts.length == 2 ? parts[1] : "desc";
    return PageRequestFactory.from(page, size, parts[0], direction, SORTABLE_FIELDS, defaultField);
  }
}
