package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.dto.ActaRequestDto;
import ec.twintailcs.electoral.acta.web.dto.ActaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.AnulacionDto;
import ec.twintailcs.electoral.acta.web.dto.DisputaDto;
import ec.twintailcs.electoral.acta.web.dto.mapper.ActaMapper;
import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
 * <ul>
 *   <li>Todas las respuestas devuelven {@link ActaResponseDto} (no la
 *       entidad {@link Acta}), evitando filtrar relaciones LAZY y
 *       posibles campos sensibles como {@code passwordHash} vía
 *       cascada. El DTO es plano: ids, estado, conteos, fecha, motivo.</li>
 *   <li>El service usa {@code findByIdWithLock} (PESSIMISTIC_WRITE) en
 *       cada transición, así dos coordinadores que tocan la misma acta
 *       se serializan.</li>
 * </ul>
 *
 * <p>Rutas:
 * <ul>
 *   <li>GET    /api/v1/coordinator/actas — listar con filtros</li>
 *   <li>GET    /api/v1/coordinator/actas/{actaId} — ver detalle</li>
 *   <li>PUT    /api/v1/coordinator/actas/{actaId}/disputar — EN_DISPUTA</li>
 *   <li>PUT    /api/v1/coordinator/actas/{actaId}/corregir — corregir EN_DISPUTA</li>
 *   <li>PUT    /api/v1/coordinator/actas/{actaId}/aprobar — aprobar ENVIADA/CORREGIDA</li>
 *   <li>DELETE /api/v1/coordinator/actas/{actaId} — anular APROBADA (solo MASTER)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/coordinator")
@PreAuthorize("hasAnyAuthority('acta:aprobar','acta:rechazar','acta:disputar','acta:read','acta:anular')")
public class CoordinatorController {

    private final ActaService actaService;
    private final ActaRepository actaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CurrentUser currentUser;
    private final ActaMapper actaMapper;

    public CoordinatorController(ActaService actaService,
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

    private Long coordinatorId() {
        return usuarioRepository.findByPublicId(currentUser.id())
            .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"))
            .getId();
    }

    @GetMapping("/actas")
    public ResponseEntity<List<ActaResponseDto>> listarActas(
            @RequestParam(required = false) EstadoActa estado,
            @RequestParam(required = false) Long jrvId,
            @RequestParam(required = false) Long cargoId) {

        List<ActaResponseDto> actas = actaRepository
                .findWithFilters(estado, jrvId, cargoId, null)
                .stream()
                .map(actaMapper::toDto)
                .toList();
        return ResponseEntity.ok(actas);
    }

    @GetMapping("/actas/{actaId}")
    public ResponseEntity<ActaResponseDto> verActa(@PathVariable Long actaId) {
        Acta acta = actaRepository.findById(actaId)
            .orElseThrow(() -> new IllegalArgumentException("Acta no encontrada: " + actaId));
        return ResponseEntity.ok(actaMapper.toDto(acta));
    }

    @PutMapping("/actas/{actaId}/disputar")
    public ResponseEntity<ActaResponseDto> disputar(@PathVariable Long actaId,
                                                     @Valid @RequestBody DisputaDto dto,
                                                     HttpServletRequest request) {
        Acta acta = actaService.marcarEnDisputa(actaId, coordinatorId(), dto.getMotivo(), request);
        return ResponseEntity.ok(actaMapper.toDto(acta));
    }

    @PutMapping("/actas/{actaId}/corregir")
    public ResponseEntity<ActaResponseDto> corregir(@PathVariable Long actaId,
                                                     @Valid @RequestBody ActaRequestDto dto,
                                                     HttpServletRequest request) {
        Acta acta = actaService.corregir(actaId, dto, coordinatorId(), true, request);
        return ResponseEntity.ok(actaMapper.toDto(acta));
    }

    @PutMapping("/actas/{actaId}/aprobar")
    public ResponseEntity<ActaResponseDto> aprobar(@PathVariable Long actaId,
                                                    HttpServletRequest request) {
        Acta acta = actaService.aprobar(actaId, coordinatorId(), request);
        return ResponseEntity.ok(actaMapper.toDto(acta));
    }

    @DeleteMapping("/actas/{actaId}")
    @PreAuthorize("hasAuthority('acta:anular')")
    public ResponseEntity<Void> anular(@PathVariable Long actaId,
                                       @Valid @RequestBody AnulacionDto dto,
                                       HttpServletRequest request) {
        actaService.anular(actaId, coordinatorId(), dto.getMotivo(), request);
        return ResponseEntity.noContent().build();
    }
}