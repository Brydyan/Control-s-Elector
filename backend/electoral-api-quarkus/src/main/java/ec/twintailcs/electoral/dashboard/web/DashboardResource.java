package ec.twintailcs.electoral.dashboard.web;

import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.dashboard.application.DashboardService;
import ec.twintailcs.electoral.dashboard.web.dto.NovedadDto;
import ec.twintailcs.electoral.dashboard.web.dto.ProgresoCantonDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResultadoCargoDto;
import ec.twintailcs.electoral.dashboard.web.dto.ResumenActasDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Endpoints REST del dashboard — solo lectura.
 *
 * <p>Decisiones arquitectónicas:
 *
 * <ul>
 *   <li>Este recurso expone <b>unicamente</b> {@code @GET}. ADMIN nunca escribe — la regla se
 *       sostiene por <i>ausencia</i> de metodo, no por un guard que se pueda borrar.
 *   <li>El endpoint de novedades solo lista actas EN_DISPUTA.
 *   <li>Todos los agregados excluyen ANULADA en la query, no en el service.
 *   <li>Sin paginacion: la medicion del techo (~2700 actas) lo descarta.
 * </ul>
 *
 * <p>El guard de clase del backend Spring es {@code hasAnyRole('ADMIN','MASTER','COORDINATOR')};
 * aca se traduce a la llamada explicita del {@link Autorizador} en cada metodo (mismo efecto:
 * OBSERVER recibe 403).
 */
@Path("/api/v1/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class DashboardResource {

  @Inject Autorizador autorizador;
  @Inject DashboardService dashboardService;

  /** Conteo de actas activas por estado (excluye ANULADA). */
  @GET
  @Path("/resumen")
  public ResumenActasDto resumen() {
    autorizador.exigir("ADMIN", "MASTER", "COORDINATOR");

    return dashboardService.resumen();
  }

  /** Votos totales por candidato agrupados por cargo (excluye actas ANULADA). */
  @GET
  @Path("/resultados")
  public List<ResultadoCargoDto> resultados() {
    autorizador.exigir("ADMIN", "MASTER", "COORDINATOR");

    return dashboardService.resultados();
  }

  /** JRVs totales vs JRVs con acta no anulada por canton, con porcentaje. */
  @GET
  @Path("/progreso")
  public List<ProgresoCantonDto> progreso() {
    autorizador.exigir("ADMIN", "MASTER", "COORDINATOR");

    return dashboardService.progreso();
  }

  /** Actas en estado EN_DISPUTA con su tiempo transcurrido en minutos desde el registro. */
  @GET
  @Path("/novedades")
  public List<NovedadDto> novedades() {
    autorizador.exigir("ADMIN", "MASTER", "COORDINATOR");

    return dashboardService.novedades();
  }
}
