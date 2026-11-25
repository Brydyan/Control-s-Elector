package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * Arbol geografico completo, en {@code GET /api/v1/master/geografia}.
 *
 * <p>Cuatro niveles, anidados: provincia → canton → parroquia → JRV. El arbol se arma en una sola
 * consulta y se devuelve anidado, para que el frontend pueda pintar el arbol sin pedir mas.
 *
 * <p>Los nombres de campo siguen la convencion del proyecto (camelCase, igual que el resto de la
 * API): {@code esUrbana} y {@code totalElectores}.
 */
@RegisterForReflection
public record GeografiaTreeDto(List<ProvinciaNode> provincias) {

  /** Provincia con sus cantones. */
  @RegisterForReflection
  public record ProvinciaNode(Long id, String nombre, String codigo, List<CantonNode> cantones) {}

  /** Canton con sus parroquias. */
  @RegisterForReflection
  public record CantonNode(Long id, String nombre, String codigo, List<ParroquiaNode> parroquias) {}

  /** Parroquia con sus JRVs. */
  @RegisterForReflection
  public record ParroquiaNode(Long id, String nombre, boolean esUrbana, List<JrvNode> jrvs) {}

  /** Junta de recepcion de votos. */
  @RegisterForReflection
  public record JrvNode(
      Long id, String numero, String recinto, int totalElectores, Long recintoId) {}
}
