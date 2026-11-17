package ec.twintailcs.electoral.acta.web;

import java.util.Set;
import java.util.TreeSet;

/**
 * Parametros de paginacion y orden del listado de actas del coordinator.
 *
 * <p>Mismo contrato que el {@code PageRequestFactory} del backend Spring: {@code page}, {@code
 * size}, {@code sort} y {@code dir} son parametros separados, el size tiene un tope de 100 y el
 * campo de orden tiene que estar en la lista permitida. Los parametros invalidos fallan con {@link
 * IllegalArgumentException}, que el backend mapea a 400 con el shape estandar de error.
 *
 * <p>El paquete {@code shared/web/pagination} compartido usa otro contrato (un solo parametro
 * {@code sort} con el formato {@code "campo,direccion"}), asi que la lista blanca y la clause
 * {@code ORDER BY} de este endpoint se resuelven aca.
 */
public record ActaPageSpec(int page, int size, String campo, boolean ascendente) {

  /** Tope maximo del tamano de pagina, el mismo que en el backend Spring. */
  public static final int MAX_SIZE = 100;

  /** Campos por los que se puede ordenar el listado. */
  private static final Set<String> SORTABLE = Set.of("fechaRegistro", "id", "estado");

  /** Orden por defecto: fecha de registro descendente. */
  private static final String DEFAULT_SORT = "fechaRegistro";

  /**
   * Valida los parametros de paginacion.
   *
   * @param page indice de pagina (base 0)
   * @param size tamano de pagina
   * @param sort campo de orden
   * @param dir direccion del orden ({@code asc} o {@code desc})
   */
  public static ActaPageSpec from(int page, int size, String sort, String dir) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
    }
    String effectiveSort = (sort == null || sort.isBlank()) ? DEFAULT_SORT : sort;
    if (!SORTABLE.contains(effectiveSort)) {
      throw new IllegalArgumentException(
          "invalid sort field: "
              + effectiveSort
              + ". Allowed: "
              + String.join(", ", new TreeSet<>(SORTABLE)));
    }
    String effectiveDir = (dir == null || dir.isBlank()) ? "desc" : dir;
    boolean ascendente;
    String normalized = effectiveDir.trim().toLowerCase();
    if ("asc".equals(normalized) || "ascending".equals(normalized)) {
      ascendente = true;
    } else if ("desc".equals(normalized) || "descending".equals(normalized)) {
      ascendente = false;
    } else {
      throw new IllegalArgumentException("invalid dir: " + dir + ". Allowed: asc, desc");
    }
    return new ActaPageSpec(page, size, effectiveSort, ascendente);
  }

  /** Numero de filas a saltar para la pagina pedida. */
  public long offset() {
    return (long) page * size;
  }

  /**
   * Clause {@code ORDER BY} completa.
   *
   * <p>Igual que el backend Spring: el campo pedido manda y, si no es el id, se agrega el id
   * ascendente como desempate, para que las filas que comparten fecha de registro no salten de
   * pagina entre peticiones.
   */
  public String orden() {
    String columna = columna(campo());
    String direccion = ascendente() ? "asc" : "desc";
    if ("id".equals(campo())) {
      return " order by " + columna + " " + direccion;
    }
    return " order by " + columna + " " + direccion + ", a.id asc";
  }

  /** Columna fisica del campo ordenable. */
  private static String columna(String campo) {
    return switch (campo) {
      case "id" -> "a.id";
      case "estado" -> "a.estado";
      default -> "a.fecha_registro";
    };
  }
}
