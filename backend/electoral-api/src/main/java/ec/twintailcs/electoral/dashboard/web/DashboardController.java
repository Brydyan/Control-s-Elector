package ec.twintailcs.electoral.dashboard.web;

import ec.twintailcs.electoral.dashboard.service.DashboardService;
import ec.twintailcs.electoral.dashboard.web.dto.NovedadDto;
import ec.twintailcs.electoral.dashboard.web.dto.ProgresoCantonDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResultadoCargoDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResumenActasDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST del dashboard — sólo lectura (RF-DASH-01..07 + RNF-DASH-01).
 *
 * <p>Decisiones arquitectónicas (ver {@code design.md}):
 *
 * <ul>
 *   <li>D2: este controlador expone <b>únicamente</b> {@code @GetMapping}. ADMIN nunca escribe — la
 *       regla se sostiene por <i>ausencia</i> de método, no por un guard que se pueda borrar. Si
 *       alguien añade un {@code @PostMapping}/{@code @PutMapping}/{@code @PatchMapping}/{@code
 *       @DeleteMapping} aquí, RF-DASH-02 lo cazará por reflexión.
 *   <li>D3: el endpoint de novedades sólo lista actas EN_DISPUTA. La inclusión de evidencias se
 *       deja para T-BE-16 (enum sin definir).
 *   <li>D1: todos los agregados excluyen ANULADA en query, no en service.
 *   <li>D5: sin paginación — medición del techo (~2700 actas) lo descarta.
 * </ul>
 *
 * <p>El {@code @PreAuthorize} a nivel de clase cubre los cuatro endpoints de una sola vez; los
 * roles ADMIN, MASTER y COORDINATOR pasan, OBSERVER recibe 403.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','MASTER','COORDINATOR')")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  /** RF-DASH-03: conteo por estado de las actas activas (sin ANULADA). */
  @GetMapping("/resumen")
  public ResponseEntity<ResumenActasDto> resumen() {
    return ResponseEntity.ok(dashboardService.resumen());
  }

  /** RF-DASH-04: votos por candidato, agrupados por cargo, sin ANULADA. */
  @GetMapping("/resultados")
  public ResponseEntity<List<ResultadoCargoDto>> resultados() {
    return ResponseEntity.ok(dashboardService.resultados());
  }

  /** RF-DASH-05: progreso por cantón (JRVs totales vs JRVs con acta no anulada). */
  @GetMapping("/progreso")
  public ResponseEntity<List<ProgresoCantonDto>> progreso() {
    return ResponseEntity.ok(dashboardService.progreso());
  }

  /** RF-DASH-06 + RF-DASH-07: actas EN_DISPUTA con su antigüedad en minutos (UTC). */
  @GetMapping("/novedades")
  public ResponseEntity<List<NovedadDto>> novedades() {
    return ResponseEntity.ok(dashboardService.novedades());
  }
}
