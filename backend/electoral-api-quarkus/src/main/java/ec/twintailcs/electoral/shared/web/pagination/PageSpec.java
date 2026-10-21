package ec.twintailcs.electoral.shared.web.pagination;

import java.util.Set;
import java.util.TreeSet;

/**
 * Parametros de paginacion validados.
 *
 * <p>Mismo contrato de query params que el backend Spring: {@code page}, {@code size} y {@code
 * sort}. El {@code sort} es un UNICO parametro con el formato {@code "campo,direccion"} (por
 * ejemplo {@code "creadoEn,desc"}), no dos params separados. El size tiene un tope de 100 y el
 * campo de orden tiene que estar en la lista permitida.
 */
public record PageSpec(int page, int size, String campo, boolean ascendente) {

  public static final int MAX_SIZE = 100;

  public static PageSpec from(
      int page, int size, String sort, Set<String> allowedSorts, String defaultSort) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
    }
    if (!allowedSorts.contains(defaultSort)) {
      throw new IllegalArgumentException(
          "invalid defaultSort: "
              + defaultSort
              + ". Allowed: "
              + String.join(", ", new TreeSet<>(allowedSorts)));
    }
    // "campo,direccion" -> el worker de Spring hace lo mismo en su helper pageable().
    String[] parts = sort == null ? new String[] {defaultSort} : sort.split(",", 2);
    String campo = parts[0];
    String direccion = parts.length == 2 ? parts[1] : "desc";
    String effectiveSort = (campo == null || campo.isBlank()) ? defaultSort : campo;
    if (!allowedSorts.contains(effectiveSort)) {
      throw new IllegalArgumentException(
          "invalid sort field: "
              + effectiveSort
              + ". Allowed: "
              + String.join(", ", new TreeSet<>(allowedSorts)));
    }
    String effectiveDir = (direccion == null || direccion.isBlank()) ? "desc" : direccion;
    String normalized = effectiveDir.toLowerCase();
    boolean ascendente;
    if ("asc".equals(normalized) || "ascending".equals(normalized)) {
      ascendente = true;
    } else if ("desc".equals(normalized) || "descending".equals(normalized)) {
      ascendente = false;
    } else {
      throw new IllegalArgumentException("invalid dir: " + effectiveDir + ". Allowed: asc, desc");
    }
    return new PageSpec(page, size, effectiveSort, ascendente);
  }
}
