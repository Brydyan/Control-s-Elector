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
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.JrvCargo;
import ec.twintailcs.electoral.electoral.domain.JrvCargoRepository;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.JrvRepository;
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
 * Endpoints REST usados por el OBSERVER para registrar y corregir actas.
 *
 * Rutas:
 *   GET  /api/v1/observer/mis-actas                    # propias actas (filtro RLS: registrada_por)
 *   GET  /api/v1/observer/correcciones-pendientes       # propias actas EN_DISPUTA
 *   GET  /api/v1/observer/jrvs                          # JRVs + resumen de cargos pendientes/registrados
 *   GET  /api/v1/observer/jrv/{jrvId}/cargos             # cargos aplicables a una JRV + estado de registro
 *   POST /api/v1/observer/actas                          # registrar nueva acta
 *   PUT  /api/v1/observer/actas/{actaId}/corregir         # corregir acta propia EN_DISPUTA
 *   POST /api/v1/observer/actas/{actaId}/imagen           # TODO: subir imagen (T-BE-14)
 */
@RestController
@RequestMapping("/api/v1/observer")
public class ObserverController {

    private final ActaService actaService;
    private final ActaRepository actaRepository;
    private final ActaMapper actaMapper;
    private final CurrentUser currentUser;
    private final UsuarioRepository usuarioRepository;
    private final JrvRepository jrvRepository;
    private final JrvCargoRepository jrvCargoRepository;

    public ObserverController(ActaService actaService,
                               ActaRepository actaRepository,
                               ActaMapper actaMapper,
                               CurrentUser currentUser,
                               UsuarioRepository usuarioRepository,
                               JrvRepository jrvRepository,
                               JrvCargoRepository jrvCargoRepository) {
        this.actaService = actaService;
        this.actaRepository = actaRepository;
        this.actaMapper = actaMapper;
        this.currentUser = currentUser;
        this.usuarioRepository = usuarioRepository;
        this.jrvRepository = jrvRepository;
        this.jrvCargoRepository = jrvCargoRepository;
    }

    private Long observerId() {
        return usuarioRepository.findByPublicId(currentUser.id())
            .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"))
            .getId();
    }

    /**
     * GET /api/v1/observer/mis-actas
     * Actas registradas por el OBSERVER autenticado. Filtro opcional por estado.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/mis-actas")
    public List<ActaResponseDto> misActas(@RequestParam(required = false) EstadoActa estado) {
        Long observerId = observerId();
        return actaRepository.findWithFilters(estado, null, null, observerId)
            .stream()
            .map(actaMapper::toDto)
            .toList();
    }

    /**
     * GET /api/v1/observer/correcciones-pendientes
     * Actas EN_DISPUTA del OBSERVER autenticado que necesitan corrección.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/correcciones-pendientes")
    public List<ActaResponseDto> correccionesPendientes() {
        Long observerId = observerId();
        return actaRepository.findWithFilters(EstadoActa.EN_DISPUTA, null, null, observerId)
            .stream()
            .map(actaMapper::toDto)
            .toList();
    }

    /**
     * GET /api/v1/observer/jrvs
     * Todas las JRVs con el resumen de cargos pendientes/registrados (actas no ANULADA).
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/jrvs")
    public List<JrvConEstadoDto> jrvs() {
        return jrvRepository.findAll().stream().map(this::toJrvConEstadoDto).toList();
    }

    private JrvConEstadoDto toJrvConEstadoDto(Jrv jrv) {
        int totalCargos = jrvCargoRepository.findByJrvIdAndActivo(jrv.getId(), true).size();
        long registrados = actaRepository.findByJrvId(jrv.getId()).stream()
            .filter(a -> a.getEstado() != EstadoActa.ANULADA)
            .map(a -> a.getCargo().getId())
            .distinct()
            .count();
        int pendientes = Math.max(0, totalCargos - (int) registrados);
        String parroquia = jrv.getParroquia() != null ? jrv.getParroquia().getNombre() : null;
        return new JrvConEstadoDto(jrv.getId(), jrv.getNumero(), parroquia, pendientes, (int) registrados);
    }

    /**
     * GET /api/v1/observer/jrv/{jrvId}/cargos
     * Cargos aplicables a la JRV indicando si ya existe acta activa registrada.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/jrv/{jrvId}/cargos")
    public List<CargoConEstadoDto> cargosDeJrv(@PathVariable Long jrvId) {
        List<JrvCargo> jrvCargos = jrvCargoRepository.findByJrvIdAndActivo(jrvId, true);

        Map<Long, Acta> actaActivaPorCargo = actaRepository.findByJrvId(jrvId).stream()
            .filter(a -> a.getEstado() != EstadoActa.ANULADA)
            .collect(Collectors.toMap(a -> a.getCargo().getId(), Function.identity(), (a, b) -> a));

        return jrvCargos.stream().map(jc -> {
            Cargo cargo = jc.getCargo();
            Acta acta = actaActivaPorCargo.get(cargo.getId());
            if (acta == null) {
                return CargoConEstadoDto.sinRegistrar(cargo.getId(), cargo.getNombre(), cargo.getNivel().name());
            }
            return CargoConEstadoDto.registrado(
                cargo.getId(), cargo.getNombre(), cargo.getNivel().name(), acta.getEstado(), acta.getId());
        }).toList();
    }

    /**
     * POST /api/v1/observer/actas
     * Registra una nueva acta. La JRV se elige libremente (sin pre-asignación).
     */
    @PreAuthorize("@authz.has('acta:upload')")
    @PostMapping("/actas")
    public ResponseEntity<ActaResponseDto> registrarActa(@Valid @RequestBody ActaRequestDto dto,
                                                           HttpServletRequest request) {
        Acta acta = actaService.registrar(dto, observerId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(actaMapper.toDto(acta));
    }

    /**
     * PUT /api/v1/observer/actas/{actaId}/corregir
     * El OBSERVER corrige su propia acta EN_DISPUTA.
     */
    @PreAuthorize("@authz.has('acta:upload')")
    @PutMapping("/actas/{actaId}/corregir")
    public ActaResponseDto corregirActa(@PathVariable Long actaId,
                                         @Valid @RequestBody ActaRequestDto dto,
                                         HttpServletRequest request) {
        // false = no es coordinator, es el propio observer corrigiendo su acta
        Acta acta = actaService.corregir(actaId, dto, observerId(), false, request);
        return actaMapper.toDto(acta);
    }

    /**
     * POST /api/v1/observer/actas/{actaId}/imagen
     * Placeholder: la integracion real con Supabase Storage se conecta en T-BE-14.
     */
    @PreAuthorize("@authz.has('acta:upload')")
    @PostMapping(value = "/actas/{actaId}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> subirImagen(@PathVariable Long actaId,
                                             @RequestPart("imagen") MultipartFile imagen) {
        // TODO(T-BE-14): subir a Supabase Storage y persistir Acta.storageUrl.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
