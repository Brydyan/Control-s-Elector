package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.dto.ActaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.AnulacionDto;
import ec.twintailcs.electoral.acta.web.dto.DisputaDto;
import ec.twintailcs.electoral.acta.web.dto.mapper.ActaMapper;
import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.shared.web.pagination.PageRequestFactory;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
  private final UsuarioRepository usuarioRepository;
  private final CurrentUser currentUser;
  private final ActaMapper actaMapper;

  public CoordinatorController(
      ActaService actaService,
      ActaRepository actaRepository,
      UsuarioRepository usuarioRepository,
      CurrentUser currentUser,
      ActaMapper actaMapper) {
    this.actaService = actaService;
    this.actaRepository = actaRepository;
    this.usuarioRepository = usuarioRepository;
    this.currentUser = currentUser;
    this.actaMapper = actaMapper;
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

    Page<Acta> result =
        actaRepository.findPageWithFilters(
            estado,
            jrvId,
            cargoFiltro,
            toLikePattern(searchNorm),
            parseSearchId(searchNorm),
            parseEstadoSearch(searchNorm),
            pageable);

    return ResponseEntity.ok(PagedResponse.of(result.map(actaMapper::toDto)));
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

  @GetMapping("/actas/{actaId}")
  public ResponseEntity<ActaResponseDto> verActa(@PathVariable Long actaId) {
    Acta acta =
        actaRepository
            .findById(actaId)
            .orElseThrow(() -> new IllegalArgumentException("Acta no encontrada: " + actaId));
    return ResponseEntity.ok(actaMapper.toDto(acta));
  }

  @PutMapping("/actas/{actaId}/disputar")
  public ResponseEntity<ActaResponseDto> disputar(
      @PathVariable Long actaId, @Valid @RequestBody DisputaDto dto, HttpServletRequest request) {
    Acta acta = actaService.marcarEnDisputa(actaId, coordinatorId(), dto.getMotivo(), request);
    return ResponseEntity.ok(actaMapper.toDto(acta));
  }

  @PutMapping("/actas/{actaId}/corregir")
  public ResponseEntity<ActaResponseDto> corregir(
      @PathVariable Long actaId,
      @Valid @RequestBody ActaRequestDto dto,
      HttpServletRequest request) {
    Acta acta = actaService.corregir(actaId, dto, coordinatorId(), true, request);
    return ResponseEntity.ok(actaMapper.toDto(acta));
  }

  @PutMapping("/actas/{actaId}/aprobar")
  public ResponseEntity<ActaResponseDto> aprobar(
      @PathVariable Long actaId, HttpServletRequest request) {
    Acta acta = actaService.aprobar(actaId, coordinatorId(), request);
    return ResponseEntity.ok(actaMapper.toDto(acta));
  }

  @DeleteMapping("/actas/{actaId}")
  @PreAuthorize("hasAuthority('acta:anular')")
  public ResponseEntity<Void> anular(
      @PathVariable Long actaId, @Valid @RequestBody AnulacionDto dto, HttpServletRequest request) {
    actaService.anular(actaId, coordinatorId(), dto.getMotivo(), request);
    return ResponseEntity.noContent().build();
  }
}
