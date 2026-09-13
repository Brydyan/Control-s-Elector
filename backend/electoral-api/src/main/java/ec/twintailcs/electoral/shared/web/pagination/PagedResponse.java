package ec.twintailcs.electoral.shared.web.pagination;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Generic paginated envelope for list endpoints.
 *
 * <p>Kept opt-in per endpoint: small catalogs keep returning bare arrays, while high-volume
 * listings migrate to this wrapper.
 */
public record PagedResponse<T>(List<T> data, PageMeta meta) {

  /**
   * Builds an envelope from a page of items.
   *
   * @param data the current page content
   * @param page the requested 0-based page index (echoed)
   * @param size the requested page size (echoed)
   * @param totalElements the filtered total element count
   */
  public static <T> PagedResponse<T> of(List<T> data, int page, int size, long totalElements) {
    int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    return new PagedResponse<>(
        List.copyOf(data), new PageMeta(page, size, totalElements, totalPages));
  }

  /**
   * Builds an envelope straight from a Spring Data page (already mapped to DTOs).
   *
   * @param result the page as returned by the repository (content already mapped)
   */
  public static <T> PagedResponse<T> of(Page<T> result) {
    return of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
  }
}
