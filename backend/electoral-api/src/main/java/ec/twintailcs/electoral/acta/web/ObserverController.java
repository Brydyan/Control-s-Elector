package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.dto.ActaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.CargoConEstadoDto;
import ec.twintailcs.electoral.acta.web.dto.JrvConEstadoDto;
import ec.twintailcs.electoral.acta.web.dto.mapper.ActaMapper;
import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.JrvCargo;
import ec.twintailcs.electoral.electoral.domain.JrvCargoRepository;
import ec.twintailcs.electoral.evidencia.application.EvidenciaService;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.JrvRepository;
import ec.twintailcs.electoral.incidencia.application.IncidenciaService;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints REST usados por el OBSERVER para registrar, guardar en borrador y corregir actas.
 *
 * <p>Rutas:
 *
 * <ul>
 *   <li>GET /api/v1/observer/mis-actas
 *   <li>GET /api/v1/observer/correcciones-pendientes
 *   <li>GET /api/v1/observer/jrvs
 *   <li>GET /api/v1/observer/jrv/{jrvId}/cargos
 *   <li>POST /api/v1/observer/actas (alta directa como ENVIADA, legacy)
 *   <li>POST /api/v1/observer/actas/borrador (guardar o reanudar BORRADOR)
 *   <li>POST /api/v1/observer/actas/{id}/enviar (BORRADOR → ENVIADA)
 *   <li>PUT /api/v1/observer/actas/{id}/corregir (corregir EN_DISPUTA)
 *   <li>POST /api/v1/observer/actas/{id}/imagen (T-BE-14, sigue 501)
 * </ul>
 */
@Tag(
    name = "Actas - Observador",
    description = "Registro, borradores, correcciones, fotos e incidencias por veedor en JRV.")
@RestController
@RequestMapping("/api/v1/observer")
public class ObserverController {

  private final ActaService actaService;
  private final ActaRepository actaRepository;
  private final ec.twintailcs.electoral.acta.application.usecase.ConsultarActaDetalleUseCase
      consultarActaDetalleUseCase;
  private final ActaMapper actaMapper;
  private final CurrentUser currentUser;
  private final UsuarioRepository usuarioRepository;
  private final JrvRepository jrvRepository;
  private final JrvCargoRepository jrvCargoRepository;
  private final EvidenciaService evidenciaService;
  private final IncidenciaService incidenciaService;

  public ObserverController(
      ActaService actaService,
      ActaRepository actaRepository,
      ActaMapper actaMapper,
      CurrentUser currentUser,
      UsuarioRepository usuarioRepository,
      JrvRepository jrvRepository,
      JrvCargoRepository jrvCargoRepository,
      EvidenciaService evidenciaService,
      IncidenciaService incidenciaService,
      ec.twintailcs.electoral.acta.application.usecase.ConsultarActaDetalleUseCase
          consultarActaDetalleUseCase) {
    this.actaService = actaService;
    this.actaRepository = actaRepository;
    this.actaMapper = actaMapper;
    this.currentUser = currentUser;
    this.usuarioRepository = usuarioRepository;
    this.jrvRepository = jrvRepository;
    this.jrvCargoRepository = jrvCargoRepository;
    this.evidenciaService = evidenciaService;
    this.incidenciaService = incidenciaService;
    this.consultarActaDetalleUseCase = consultarActaDetalleUseCase;
  }

  private Long observerId() {
    return usuarioRepository
        .findByPublicId(currentUser.id())
        .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"))
        .getId();
  }

  @Operation(
      summary = "Mis actas",
      description =
          "Lista las actas registradas por el OBSERVER autenticado. Filtro opcional por estado.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Lista de actas del observer",
        content = @Content(schema = @Schema(implementation = ActaResponseDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("isAuthenticated()")
  @GetMapping("/mis-actas")
  @Transactional(readOnly = true)
  public List<ActaResponseDto> misActas(
      @Parameter(description = "Filtrar por estado del acta (ej. BORRADOR, ENVIADA, EN_DISPUTA)")
          @RequestParam(required = false)
          EstadoActa estado) {
    Long observerId = observerId();
    return actaRepository.findWithFilters(estado, null, null, null, observerId).stream()
        .map(actaMapper::toDto)
        .toList();
  }

  @Operation(
      summary = "Correcciones pendientes",
      description = "Lista las actas en estado EN_DISPUTA asignadas a este OBSERVER para corregir.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Lista de actas en disputa del observer",
        content = @Content(schema = @Schema(implementation = ActaResponseDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("isAuthenticated()")
  @GetMapping("/correcciones-pendientes")
  @Transactional(readOnly = true)
  public List<ActaResponseDto> correccionesPendientes() {
    Long observerId = observerId();
    return actaRepository
        .findWithFilters(EstadoActa.EN_DISPUTA, null, null, null, observerId)
        .stream()
        .map(actaMapper::toDto)
        .toList();
  }

  @Operation(
      summary = "JRVs del observer",
      description =
          "Lista todas las JRVs con su estado de registro (cargos pendientes y registrados).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Lista de JRVs con estado",
        content = @Content(schema = @Schema(implementation = JrvConEstadoDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("isAuthenticated()")
  @GetMapping("/jrvs")
  @Transactional(readOnly = true)
  public List<JrvConEstadoDto> jrvs() {
    return jrvRepository.findAll().stream().map(this::toJrvConEstadoDto).toList();
  }

  private JrvConEstadoDto toJrvConEstadoDto(Jrv jrv) {
    int totalCargos = jrvCargoRepository.findByJrvIdAndActivo(jrv.getId(), true).size();
    long registrados =
        actaRepository.findByJrvId(jrv.getId()).stream()
            .filter(a -> a.getEstado() != EstadoActa.ANULADA)
            .map(a -> a.getCargo().getId())
            .distinct()
            .count();
    int pendientes = Math.max(0, totalCargos - (int) registrados);
    String parroquia = jrv.getParroquia() != null ? jrv.getParroquia().getNombre() : null;
    return new JrvConEstadoDto(
        jrv.getId(), jrv.getNumero(), parroquia, pendientes, (int) registrados);
  }

  @Operation(
      summary = "Cargos de una JRV",
      description =
          "Lista los cargos activos de una JRV con el estado de su acta (sin registrar o con estado).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Lista de cargos con estado de acta",
        content = @Content(schema = @Schema(implementation = CargoConEstadoDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("isAuthenticated()")
  @GetMapping("/jrv/{jrvId}/cargos")
  @Transactional(readOnly = true)
  public List<CargoConEstadoDto> cargosDeJrv(
      @Parameter(description = "ID interno de la JRV") @PathVariable Long jrvId) {
    List<JrvCargo> jrvCargos = jrvCargoRepository.findByJrvIdAndActivo(jrvId, true);

    Map<Long, Acta> actaActivaPorCargo =
        actaRepository.findByJrvId(jrvId).stream()
            .filter(a -> a.getEstado() != EstadoActa.ANULADA)
            .collect(Collectors.toMap(a -> a.getCargo().getId(), Function.identity(), (a, b) -> a));

    return jrvCargos.stream()
        .map(
            jc -> {
              Cargo cargo = jc.getCargo();
              Acta acta = actaActivaPorCargo.get(cargo.getId());
              if (acta == null) {
                return CargoConEstadoDto.sinRegistrar(
                    cargo.getId(), cargo.getNombre(), cargo.getNivel().name());
              }
              return CargoConEstadoDto.registrado(
                  cargo.getId(),
                  cargo.getNombre(),
                  cargo.getNivel().name(),
                  acta.getEstado(),
                  acta.getId());
            })
        .toList();
  }

  /**
   * POST /api/v1/observer/actas — alta directa como ENVIADA. Mantenido por compatibilidad con
   * clientes que ya cargan todos los datos. Equivale a {@code guardarBorrador()} seguido de {@code
   * enviar()}.
   */
  @Operation(
      summary = "Registrar acta (alta directa)",
      description =
          "Crea y envia el acta directamente en estado ENVIADA. Requiere todos los datos completos."
              + " Uso legacy; preferir el flujo borrador + enviar.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Acta creada y enviada",
        content = @Content(schema = @Schema(implementation = ActaResponseDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Datos invalidos o suma de votos incorrecta",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Sin permiso acta:upload",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Ya existe un acta activa para esta JRV y cargo",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('acta:upload')")
  @PostMapping("/actas")
  public ResponseEntity<ActaResponseDto> registrarActa(
      @Valid @RequestBody ActaRequestDto dto, HttpServletRequest request) {
    Acta acta = actaService.registrar(dto, observerId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(actaMapper.toDto(acta));
  }

  /**
   * POST /api/v1/observer/actas/borrador — guarda o reanuda un BORRADOR. No exige integridad
   * electoral; el observador puede ir guardando mientras junta los datos del acta.
   */
  @Operation(
      summary = "Guardar borrador de acta",
      description =
          "Guarda o reanuda un acta en estado BORRADOR. No valida integridad electoral."
              + " El observer puede ir completando los datos antes de enviar.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Borrador guardado",
        content = @Content(schema = @Schema(implementation = ActaResponseDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Datos de entrada invalidos",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Sin permiso acta:upload",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('acta:upload')")
  @PostMapping("/actas/borrador")
  public ResponseEntity<ActaResponseDto> guardarBorrador(
      @Valid @RequestBody ActaRequestDto dto, HttpServletRequest request) {
    Acta acta = actaService.guardarBorrador(dto, observerId(), request);
    return ResponseEntity.ok(actaMapper.toDto(acta));
  }

  /**
   * POST /api/v1/observer/actas/{actaId}/enviar — transicion BORRADOR → ENVIADA con validacion
   * completa.
   */
  @Operation(
      summary = "Enviar borrador",
      description =
          "Transiciona el acta de BORRADOR a ENVIADA con validacion completa de integridad electoral"
              + " (suma de votos = total sufragantes).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Acta enviada",
        content = @Content(schema = @Schema(implementation = ActaResponseDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Suma de votos incorrecta o acta no en BORRADOR",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Sin permiso acta:upload o no es el dueno del acta",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Acta no encontrada",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('acta:upload')")
  @PostMapping("/actas/{actaId}/enviar")
  public ActaResponseDto enviarBorrador(
      @Parameter(description = "ID interno del acta") @PathVariable Long actaId,
      HttpServletRequest request) {
    Acta acta = actaService.enviar(actaId, observerId(), request);
    return actaMapper.toDto(acta);
  }

  @Operation(
      summary = "Corregir acta (observer)",
      description =
          "Aplica correcciones al acta en estado EN_DISPUTA segun las indicaciones del coordinador.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Acta corregida",
        content = @Content(schema = @Schema(implementation = ActaResponseDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Datos invalidos o acta no en EN_DISPUTA",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Sin permiso acta:upload",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Acta no encontrada",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('acta:upload')")
  @PutMapping("/actas/{actaId}/corregir")
  public ActaResponseDto corregirActa(
      @Parameter(description = "ID interno del acta") @PathVariable Long actaId,
      @Valid @RequestBody ActaRequestDto dto,
      HttpServletRequest request) {
    Acta acta = actaService.corregir(actaId, dto, observerId(), false, request);
    return actaMapper.toDto(acta);
  }

  /**
   * POST /api/v1/observer/actas/{actaId}/imagen — T-BE-14 cerrado en PR δ. Sube la foto del acta a
   * Supabase Storage via EvidenciaService.
   */
  @Operation(
      summary = "Subir fotografia del acta",
      description =
          "Sube imagen (JPG/PNG/WEBP, max 10MB) como evidencia fotografica del acta fisica.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Imagen subida y vinculada al acta"),
    @ApiResponse(
        responseCode = "400",
        description = "Archivo ausente, tipo no permitido o supera el limite de 10MB",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Sin permiso acta:upload o no es el dueno del acta",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('acta:upload')")
  @PostMapping(value = "/actas/{actaId}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> subirImagen(
      @Parameter(description = "ID interno del acta") @PathVariable Long actaId,
      @Parameter(description = "Archivo de imagen (max 10MB, formatos: jpg/png/webp)")
          @RequestPart("imagen")
          MultipartFile imagen) {
    evidenciaService.upload(imagen, actaId, currentUser.id());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /**
   * POST /api/v1/observer/incidencias — T-BE-19. Reporte inmediato de contingencia operativa de JRV
   * (mesa no instalada, urna violentada, etc.) por el veedor.
   */
  @Operation(
      summary = "Registrar incidencia",
      description =
          "Registra una incidencia operativa en la JRV (mesa no instalada, urna violentada, etc.).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Incidencia registrada",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            ec.twintailcs.electoral.incidencia.web.dto.IncidenciaResponseDto
                                .class))),
    @ApiResponse(
        responseCode = "400",
        description = "Datos invalidos",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Sin permiso acta:upload",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('acta:upload')")
  @PostMapping("/incidencias")
  public ResponseEntity<ec.twintailcs.electoral.incidencia.web.dto.IncidenciaResponseDto>
      registrarIncidencia(
          @Valid @org.springframework.web.bind.annotation.RequestBody
              ec.twintailcs.electoral.incidencia.web.dto.IncidenciaRequestDto dto,
          HttpServletRequest request) {
    ec.twintailcs.electoral.incidencia.domain.IncidenciaJrv inc =
        incidenciaService.registrarIncidencia(dto, currentUser.id(), request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            incidenciaService.listarIncidencias(null).stream()
                .filter(r -> r.id().equals(inc.getId()))
                .findFirst()
                .orElseThrow());
  }

  /**
   * T-BE-21: GET /api/v1/observer/actas/{actaId} — devuelve el detalle completo del acta con
   * detalles, evidencia y motivo de disputa. Solo el OBSERVER dueno puede consultar (autoria se
   * valida aqui para mantener el control de acceso cerca del endpoint).
   */
  @Operation(
      summary = "Ver detalle de mi acta",
      description =
          "Retorna el detalle completo del acta (votos por candidato, evidencia, motivo de disputa)."
              + " Solo el OBSERVER dueno puede consultar.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Detalle completo del acta",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            ec.twintailcs.electoral.acta.web.dto.ActaDetalleCompletoDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "403",
        description = "No eres el dueno de esta acta",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Acta no encontrada",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @PreAuthorize("@authz.has('acta:upload')")
  @GetMapping("/actas/{actaId}")
  public ResponseEntity<ec.twintailcs.electoral.acta.web.dto.ActaDetalleCompletoDto> verMiActa(
      @Parameter(description = "ID interno del acta") @PathVariable Long actaId) {
    ec.twintailcs.electoral.acta.domain.Acta acta =
        actaRepository
            .findById(actaId)
            .orElseThrow(
                () ->
                    new ec.twintailcs.electoral.acta.domain.ActaInvalidaException(
                        "Acta no encontrada: " + actaId));

    // Autoria — el OBSERVER solo ve SUS actas (per RF-OBS-04 escenario 2, analogo).
    Long observerId =
        usuarioRepository
            .findByPublicId(currentUser.id())
            .orElseThrow(
                () ->
                    new ec.twintailcs.electoral.acta.domain.ActaInvalidaException(
                        "Usuario autenticado no encontrado"))
            .getId();
    if (!acta.getRegistradaPor().getId().equals(observerId)) {
      throw new ec.twintailcs.electoral.acta.domain.AccesoNoPermitidoException(
          "Solo puedes consultar tus propias actas");
    }

    return ResponseEntity.ok(consultarActaDetalleUseCase.ejecutar(actaId));
  }
}
