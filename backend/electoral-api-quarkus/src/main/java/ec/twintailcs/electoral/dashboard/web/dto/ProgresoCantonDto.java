package ec.twintailcs.electoral.dashboard.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * Linea de progreso por canton, en {@code GET /api/v1/dashboard/progreso}.
 *
 * <ul>
 *   <li>{@code totalJrvs}: JRVs totales del canton.
 *   <li>{@code jrvsConActa}: JRVs con al menos un acta no anulada (DISTINCT: una JRV con tres
 *       cargos cuenta una vez).
 *   <li>{@code porcentaje}: 0..100 calculado sobre {@code totalJrvs}. Si {@code totalJrvs == 0}, el
 *       service devuelve {@code 0.0} (sin division por cero, sin NaN).
 *   <li>{@code parroquias}: desglose territorial del canton, una linea por parroquia. Va anidado y
 *       no en un endpoint aparte porque el frontend cambia de canton sin volver a pedir nada (un
 *       solo payload).
 * </ul>
 *
 * <p>El campo {@code parroquias} es aditivo: antes de existia, el frontend leia {@code parroquias}
 * de esta misma respuesta y el backend no lo devolvia, asi que el panel "Desglose de Parroquias"
 * salia siempre vacio sin fallar. El contrato de la suite {@code DashboardContractIT} ahora exige
 * que el desglose venga poblado, para que ese hueco no vuelva a ser silencioso.
 */
@RegisterForReflection
public record ProgresoCantonDto(
    Long cantonId,
    String cantonNombre,
    long totalJrvs,
    long jrvsConActa,
    double porcentaje,
    List<ParroquiaProgresoDto> parroquias) {}
