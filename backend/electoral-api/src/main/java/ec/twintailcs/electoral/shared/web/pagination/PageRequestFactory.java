package ec.twintailcs.electoral.shared.web.pagination;

import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds validated {@link Pageable} instances for paginated list endpoints.
 *
 * <p>Every violation throws {@link IllegalArgumentException}, which the existing {@code
 * GlobalExceptionHandler} already maps to {@code 400} with the standard error body — no extra
 * handler wiring needed per endpoint.
 */
public final class PageRequestFactory {

  /** Hard upper bound for page size, shared by every paginated endpoint. */
  public static final int MAX_SIZE = 100;

  private PageRequestFactory() {}

  /**
   * Validates raw paging params and builds the {@link Pageable}.
   *
   * @param page 0-based page index, must be {@code >= 0}
   * @param size page size, must satisfy {@code 1 <= size <= 100}
   * @param sort requested sort field, must be in {@code allowedSorts} (blank falls back to {@code
   *     defaultSort})
   * @param dir {@code asc|desc} (case-insensitive, blank falls back to {@code desc})
   * @param allowedSorts per-endpoint whitelist of sortable fields
   * @param defaultSort fallback sort field when {@code sort} is blank
   */
  public static Pageable from(
      int page, int size, String sort, String dir, Set<String> allowedSorts, String defaultSort) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and 100");
    }
    String effectiveSort = (sort == null || sort.isBlank()) ? defaultSort : sort;
    if (!allowedSorts.contains(effectiveSort)) {
      throw new IllegalArgumentException(
          "invalid sort field: "
              + effectiveSort
              + ". Allowed: "
              + String.join(", ", new TreeSet<>(allowedSorts)));
    }
    String effectiveDir = (dir == null || dir.isBlank()) ? Sort.Direction.DESC.name() : dir;
    Sort.Direction direction;
    try {
      direction = Sort.Direction.fromString(effectiveDir);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid dir: " + dir + ". Allowed: asc, desc");
    }
    return PageRequest.of(page, size, Sort.by(direction, effectiveSort));
  }
}
