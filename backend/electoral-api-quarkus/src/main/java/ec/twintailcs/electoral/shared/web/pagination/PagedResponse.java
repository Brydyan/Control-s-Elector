package ec.twintailcs.electoral.shared.web.pagination;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * Envoltura de una pagina de resultados. Mismo shape que el backend Spring: {@code {data: [...],
 * meta: {page, size, totalElements, totalPages}}}.
 */
@RegisterForReflection
public record PagedResponse<T>(List<T> data, PageMeta meta) {

  public static <T> PagedResponse<T> of(List<T> data, int page, int size, long totalElements) {
    int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    return new PagedResponse<>(
        List.copyOf(data), new PageMeta(page, size, totalElements, totalPages));
  }
}
