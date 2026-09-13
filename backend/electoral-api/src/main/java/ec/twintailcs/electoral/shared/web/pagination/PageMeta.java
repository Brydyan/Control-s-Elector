package ec.twintailcs.electoral.shared.web.pagination;

/**
 * Pagination metadata echoed from the request plus filtered totals.
 *
 * @param page the requested 0-based page index (echoed)
 * @param size the requested page size (echoed)
 * @param totalElements the filtered total element count
 * @param totalPages the total page count, {@code ceil(totalElements / size)}
 */
public record PageMeta(int page, int size, long totalElements, int totalPages) {}
