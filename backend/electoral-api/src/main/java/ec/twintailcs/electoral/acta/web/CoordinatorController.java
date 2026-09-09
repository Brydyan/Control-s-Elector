package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.dto.AnulacionDto;
import ec.twintailcs.electoral.acta.web.dto.DisputaDto;
import ec.twintailcs.electoral.acta.web.dto.mapper.ActaMapper;
import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
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
 * Endpoints REST para COORDINATOR y MASTER.
 * Ambos roles pueden disputar, corregir, aprobar actas.
 * Solo MASTER puede anular.
 *
 * Rutas:
 *   GET  /api/v1/coordinator/actas                    # listar con filtros
 *   GET  /api/v1/coordinator/actas/{actaId}            # ver detalle
 *   PUT  /api/v1/coordinator/actas/{actaId}/disputar   # marcar EN_DISPUTA
 *   PUT  /api/v1/coordinator/actas/{actaId}/corregir   # corregir EN_DISPUTA
 *   PUT  /api/v1/coordinator/actas/{actaId}/aprobar    # aprobar ENVIADA/CORREGIDA
 *   DELETE /api/v1/coordinator/actas/{actaId}          # anular APROBADA (solo MASTER)
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

    /**
     * GET /api/v1/coordinator/actas
     * Lista actas globales con filtros opcionales.
     * Ejemplo: GET /api/v1/coordinator/actas?estado=ENVIADA&jrvId=5
     */
    @GetMapping("/actas")
    public ResponseEntity<List<Acta>> listarActas(
            @RequestParam(required = false) EstadoActa estado,
            @RequestParam(required = false) Long jrvId,
            @RequestParam(required = false) Long cargoId) {

        List<Acta> actas = actaRepository.findWithFilters(estado, jrvId, cargoId, null);
        return ResponseEntity.ok(actas);
    }

    /**
     * GET /api/v1/coordinator/actas/{actaId}
     * Ver detalle de un acta específica.
     */
    @GetMapping("/actas/{actaId}")
    public ResponseEntity<Acta> verActa(@PathVariable Long actaId) {
        Acta acta = actaRepository.findById(actaId)
            .orElseThrow(() -> new IllegalArgumentException("Acta no encontrada: " + actaId));
        return ResponseEntity.ok(acta);
    }

    /**
     * PUT /api/v1/coordinator/actas/{actaId}/disputar
     * Marcar un acta como EN_DISPUTA, con motivo obligatorio.
     */
    @PutMapping("/actas/{actaId}/disputar")
    public ResponseEntity<Acta> disputar(@PathVariable Long actaId,
                                         @Valid @RequestBody DisputaDto dto,
                                         HttpServletRequest request) {
        Acta acta = actaService.marcarEnDisputa(actaId, coordinatorId(), dto.getMotivo(), request);
        return ResponseEntity.ok(acta);
    }

    /**
     * PUT /api/v1/coordinator/actas/{actaId}/corregir
     * COORDINATOR corrige directamente el acta EN_DISPUTA.
     */
    @PutMapping("/actas/{actaId}/corregir")
    public ResponseEntity<Acta> corregir(@PathVariable Long actaId,
                                         @Valid @RequestBody ActaRequestDto dto,
                                         HttpServletRequest request) {
        Acta acta = actaService.corregir(actaId, dto, coordinatorId(), true, request);
        return ResponseEntity.ok(acta);
    }

    /**
     * PUT /api/v1/coordinator/actas/{actaId}/aprobar
     * Aprobar un acta ENVIADA o CORREGIDA.
     */
    @PutMapping("/actas/{actaId}/aprobar")
    public ResponseEntity<Acta> aprobar(@PathVariable Long actaId,
                                        HttpServletRequest request) {
        Acta acta = actaService.aprobar(actaId, coordinatorId(), request);
        return ResponseEntity.ok(acta);
    }

    /**
     * DELETE /api/v1/coordinator/actas/{actaId}
     * Anular un acta APROBADA. SOLO MASTER puede hacer esto.
     */
    @DeleteMapping("/actas/{actaId}")
    @PreAuthorize("hasAuthority('acta:anular')")
    public ResponseEntity<Void> anular(@PathVariable Long actaId,
                                       @Valid @RequestBody AnulacionDto dto,
                                       HttpServletRequest request) {
        actaService.anular(actaId, coordinatorId(), dto.getMotivo(), request);
        return ResponseEntity.noContent().build();
    }
}
