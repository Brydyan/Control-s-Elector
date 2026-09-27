package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.dto.ActaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.AnulacionDto;
import ec.twintailcs.electoral.acta.web.dto.DisputaDto;
import ec.twintailcs.electoral.acta.web.dto.HistorialActaDto;
import ec.twintailcs.electoral.acta.web.dto.mapper.ActaMapper;
import ec.twintailcs.electoral.auditlog.domain.AuditLog;
import ec.twintailcs.electoral.auditlog.domain.AuditLogRepository;
import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.evidencia.application.EvidenciaService;
import ec.twintailcs.electoral.incidencia.application.IncidenciaService;
import ec.twintailcs.electoral.shared.web.pagination.PageRequestFactory;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Endpoints REST para COORDINATOR y MASTER.
 *
 * <p>Cambios en PR γ:
 *
 * <ul>
 *   <li>Todas las respuestas devuelven {@link ActaResponseDto} (no la entidad {@link Acta}),
 *       evitando filtrar relaciones LAZY y posibles campos sensibles como {@code passwordHash} vía
 *       cascada. El DTO es plano: ids, estado, conteos, fecha, motivo.
 *   <li>El service usa {@code findByIdWithLock} (PESSIMISTIC_WRITE) en cada transición, así dos
 *       coordinadores que tocan la misma acta se serializan.
 * </ul>
 *
 * <p>Rutas:
 *
 * <ul>
 *   <li>GET /api/v1/coordinator/actas — listar con filtros
 *   <li>GET /api/v1/coordinator/actas/{actaId} — ver detalle
 *   <li>PUT /api/v1/coordinator/actas/{actaId}/disputar — EN_DISPUTA
 *   <li>PUT /api/v1/coordinator/actas/{actaId}/corregir — corregir EN_DISPUTA
 *   <li>PUT /api/v1/coordinator/actas/{actaId}/aprobar — aprobar ENVIADA/CORREGIDA
 *   <li>DELETE /api/v1/coordinator/actas/{actaId} — anular APROBADA (solo MASTER)
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/coordinator")
@PreAuthorize(
    "hasAnyAuthority('acta:aprobar','acta:rechazar','acta:disputar','acta:read','acta:anular')")
public class CoordinatorController {

  private final ActaService actaService;
  private final ActaRepository actaRepository;
  private final ActaDetalleRepository actaDetalleRepository;
  private final UsuarioRepository usuarioRepository;
  private final EvidenciaService evidenciaService;
  private final AuditLogRepository auditLogRepository;
  private final CurrentUser currentUser;
  private final ActaMapper actaMapper;
  private final IncidenciaService incidenciaService;
  private final ec.twintailcs.electoral.acta.application.ExpedientePdfService expedientePdfService;
  private final ec.twintailcs.electoral.acta.application.usecase.ConsultarActaDetalleUseCase
      consultarActaDetalleUseCase;

  public CoordinatorController(
      ActaService actaService,
      ActaRepository actaRepository,
      ActaDetalleRepository actaDetalleRepository,
      UsuarioRepository usuarioRepository,
      AuditLogRepository auditLogRepository,
      CurrentUser currentUser,
      ActaMapper actaMapper,
      EvidenciaService evidenciaService,
      IncidenciaService incidenciaService,
      ec.twintailcs.electoral.acta.application.ExpedientePdfService expedientePdfService,
      ec.twintailcs.electoral.acta.application.usecase.ConsultarActaDetalleUseCase
          consultarActaDetalleUseCase) {
    this.actaService = actaService;
    this.actaRepository = actaRepository;
    this.actaDetalleRepository = actaDetalleRepository;
    this.usuarioRepository = usuarioRepository;
    this.auditLogRepository = auditLogRepository;
    this.currentUser = currentUser;
    this.actaMapper = actaMapper;
    this.evidenciaService = evidenciaService;
    this.incidenciaService = incidenciaService;
    this.expedientePdfService = expedientePdfService;
    this.consultarActaDetalleUseCase = consultarActaDetalleUseCase;
  }

  private static final Set<String> SORTABLE_FIELDS = Set.of("fechaRegistro", "id", "estado");

  private static final String DEFAULT_SORT = "fechaRegistro";

  private Long coordinatorId() {
    return usuarioRepository
        .findByPublicId(currentUser.id())
        .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"))
        .getId();
  }

  /**
   * Lists actas with server-side pagination, filtering and search.
   *
   * <p>Semantics: {@code dignidad} ≡ {@code cargo} — {@code dignidadId} is accepted as an alias of
   * {@code cargoId} (explicit {@code cargoId} wins when both are present) so UI code that already
   * speaks of "dignidades" keeps working. {@code search} matches case-insensitively against the JRV
   * number and the acta id as string, plus an exact id match when numeric, plus an exact {@code
   * EstadoActa} match when the term names a state (e.g. "enviada", "EN_DISPUTA"). Default order is
   * {@code fechaRegistro DESC}. Invalid paging params fail with 400 via {@code
   * GlobalExceptionHandler} (existing error format) — validation lives in the shared {@code
   * PageRequestFactory}.
   */
  @GetMapping("/actas")
  public ResponseEntity<PagedResponse<ActaResponseDto>> listarActas(
      @RequestParam(required = false) EstadoActa estado,
      @RequestParam(required = false) Long jrvId,
      @RequestParam(required = false) Long cargoId,
      @RequestParam(required = false) Long cantonId,
      @RequestParam(required = false) Long dignidadId,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "fechaRegistro") String sort,
      @RequestParam(defaultValue = "desc") String dir) {

    Pageable pageable =
        PageRequestFactory.from(page, size, sort, dir, SORTABLE_FIELDS, DEFAULT_SORT);

    Long cargoFiltro = cargoId != null ? cargoId : dignidadId;
    String searchNorm = (search == null || search.isBlank()) ? null : search.trim();

    // DTO projection: denominatives + candidate-votes subtotal in one statement, no N+1.
    Page<ActaResponseDto> result =
        actaRepository.findPageWithFilters(
            estado,
            jrvId,
            cargoFiltro,
            cantonId,
            toLikePattern(searchNorm),
            parseSearchId(searchNorm),
            parseEstadoSearch(searchNorm),
            pageable);

    return ResponseEntity.ok(PagedResponse.of(result));
  }

  /**
   * Builds a case-insensitive LIKE pattern from user input. Precomputed in Java (never with SQL
   * LOWER()/CONCAT() around a bind parameter) so a NULL search keeps the parameter unambiguously
   * typed and Postgres never infers bytea for it.
   */
  private static String toLikePattern(String search) {
    if (search == null) {
      return null;
    }
    return "%" + escapeLike(search.toLowerCase()) + "%";
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Long parseSearchId(String search) {
    if (search == null) {
      return null;
    }
    try {
      return Long.valueOf(search);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Resolves a free-text search term to an {@link EstadoActa} when it names a state
   * (case-insensitive; {@code -} and spaces normalized to {@code _}). Returns {@code null} when the
   * term names no state, leaving current JRV/id matching untouched.
   */
  static EstadoActa parseEstadoSearch(String search) {
    if (search == null || search.isBlank()) {
      return null;
    }
    String normalized = search.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    try {
      return EstadoActa.valueOf(normalized);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * T-BE-21: vista de detalle con detalles, evidencia (URL prefirmada) y motivoDisputa si
   * aplica. Reemplaza la versión plana de {@link ActaResponseDto} (que sigue usándose en el
   * listado para evitar N+1).
   */
  @GetMapping("/actas/{actaId}")
  public ResponseEntity<ec.twintailcs.electoral.acta.web.dto.ActaDetalleCompletoDto>
      verActaDetalle(@PathVariable Long actaId) {
    return ResponseEntity.ok(consultarActaDetalleUseCase.ejecutar(actaId));
  }

  /**
   * Devuelve el historial de transiciones del acta (cada acción crítica queda registrada en {@code
   * audit_log}). Solo expone entradas de la propia entidad y nunca el log global del sistema.
   * Ordenado de más reciente a más antiguo.
   */
  @GetMapping("/actas/{actaId}/historial")
  @Transactional(readOnly = true)
  public ResponseEntity<List<HistorialActaDto>> historialActa(@PathVariable Long actaId) {
    List<AuditLog> entries =
        auditLogRepository.findByEntidadAndEntidadIdOrderByTimestampDesc("acta", actaId);
    List<HistorialActaDto> dto =
        entries.stream()
            .map(
                e -> {
                  String nombre = null;
                  String rol = null;
                  if (e.getRegistradoPor() != null) {
                    var usuario = usuarioRepository.findById(e.getRegistradoPor()).orElse(null);
                    if (usuario != null) {
                      nombre = usuario.getNombre() + " " + usuario.getApellido();
                      rol = usuario.getRol() != null ? usuario.getRol().getNombre() : null;
                    }
                  }
                  return new HistorialActaDto(
                      e.getId(),
                      e.getAccion(),
                      e.getTimestamp(),
                      nombre,
                      rol,
                      e.getIpAddress(),
                      e.getDetalle() == null ? null : e.getDetalle().toString());
                })
            .toList();
    return ResponseEntity.ok(dto);
  }

  @PutMapping("/actas/{actaId}/disputar")
  public ResponseEntity<ActaResponseDto> disputar(
      @PathVariable Long actaId, @Valid @RequestBody DisputaDto dto, HttpServletRequest request) {
    Acta acta = actaService.marcarEnDisputa(actaId, coordinatorId(), dto.getMotivo(), request);
    return ResponseEntity.ok(toDto(acta));
  }

  @PutMapping("/actas/{actaId}/corregir")
  public ResponseEntity<ActaResponseDto> corregir(
      @PathVariable Long actaId,
      @Valid @RequestBody ActaRequestDto dto,
      HttpServletRequest request) {
    Acta acta = actaService.corregir(actaId, dto, coordinatorId(), true, request);
    return ResponseEntity.ok(toDto(acta));
  }

  @PutMapping("/actas/{actaId}/aprobar")
  public ResponseEntity<ActaResponseDto> aprobar(
      @PathVariable Long actaId, HttpServletRequest request) {
    Acta acta = actaService.aprobar(actaId, coordinatorId(), request);
    return ResponseEntity.ok(toDto(acta));
  }

  @DeleteMapping("/actas/{actaId}")
  @PreAuthorize("hasAuthority('acta:anular')")
  public ResponseEntity<Void> anular(
      @PathVariable Long actaId, @Valid @RequestBody AnulacionDto dto, HttpServletRequest request) {
    actaService.anular(actaId, coordinatorId(), dto.getMotivo(), request);
    return ResponseEntity.noContent().build();
  }

  /**
   * Maps a single acta to its response DTO, patching the candidate-votes subtotal the mapper cannot
   * load (single extra aggregated query — no N+1 on single-item paths).
   */
  private ActaResponseDto toDto(Acta acta) {
    ActaResponseDto base = actaMapper.toDto(acta);
    Long subtotal = actaDetalleRepository.sumarVotosPorActaId(acta.getId());
    return new ActaResponseDto(
        base.id(),
        base.jrvId(),
        base.cargoId(),
        base.registradaPorId(),
        base.estado(),
        base.totalSufragantes(),
        base.votosBlancos(),
        base.votosNulos(),
        base.fechaRegistro(),
        base.motivoAnulacion(),
        base.nombreCargo(),
        base.numeroJrv(),
        subtotal);
  }

  /**
   * T-BE-18: subida de evidencia fotográfica por COORDINATOR/MASTER como contingencia.
   *
   * <p>Camino normal: OBSERVER sube (POST /api/v1/observer/actas/{id}/imagen). Esta ruta es para
   * cuando el OBSERVER no pudo hacerlo (equipo roto, emergencia) y el COORDINATOR toma el relevo.
   * La subida va al mismo {@code SubirEvidenciaUseCase} que la del OBSERVER, pero además registra
   * una fila de auditoría forense con la acción {@code EVIDENCIA_CONTINGENCIA_COORDINADOR}.
   *
   * <p>{@code @PreAuthorize("hasAnyRole('COORDINATOR','MASTER')")} refuerza la restricción del
   * nivel de clase (que es por permisos). OBSERVER recibe 403; ADMIN igual.
   */
  @PreAuthorize("hasAnyRole('COORDINATOR','MASTER')")
  @PostMapping(value = "/actas/{actaId}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> subirImagen(
      @PathVariable Long actaId,
      @RequestPart("imagen") MultipartFile imagen,
      HttpServletRequest request) {
    evidenciaService.subirEvidenciaCoordinador(actaId, imagen, CurrentUser.staticId(), request);
    return ResponseEntity.status(201).build();
  }

  /**
   * T-BE-19: GET /api/v1/coordinator/incidencias — listado para monitoreo. Filtro opcional por
   * {@code jrvId}.
   *
   * <p>Doble capa de protección (per design.md D2): {@code @PreAuthorize} a nivel de método
   * refuerza el de la clase. Si en el futuro alguien quita el de la clase (p.ej. para unificar
   * permisos), el método sigue protegido — defense in depth.
   */
  @PreAuthorize("hasAnyRole('COORDINATOR','MASTER','ADMIN')")
  @GetMapping("/incidencias")
  public java.util.List<ec.twintailcs.electoral.incidencia.web.dto.IncidenciaResponseDto>
      listarIncidencias(@RequestParam(required = false) Long jrvId) {
    return incidenciaService.listarIncidencias(jrvId);
  }

  /**
   * T-BE-20: GET /api/v1/coordinator/actas/{actaId}/expediente-pdf — descarga el PDF del expediente
   * de impugnación CNE en memoria (sin tocar disco). Devuelve bytes con Content-Type
   * application/pdf (via produces) y Content-Disposition inline para visualizar en navegador.
   */
  @PreAuthorize("hasAnyRole('COORDINATOR','MASTER','ADMIN')")
  @GetMapping(value = "/actas/{actaId}/expediente-pdf", produces = "application/pdf")
  public org.springframework.http.ResponseEntity<byte[]> descargarExpedientePdf(
      @PathVariable Long actaId) {
    byte[] pdf = expedientePdfService.generarExpedientePdf(actaId, CurrentUser.staticId());
    String filename = "expediente-acta-" + actaId + ".pdf";
    return org.springframework.http.ResponseEntity.ok()
        .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
        .body(pdf);
  }
}
