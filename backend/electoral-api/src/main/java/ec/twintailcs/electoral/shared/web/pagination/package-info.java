/**
 * Shared pagination envelope plus request validation for list endpoints.
 *
 * <p>Reuse in the next endpoint in 3 steps:
 *
 * <ol>
 *   <li>Repository returns a Spring Data {@code Page} (e.g. via a filtered query method).
 *   <li>Controller builds the {@code Pageable} with {@link
 *       ec.twintailcs.electoral.shared.web.pagination.PageRequestFactory#from(int, int, String,
 *       String, java.util.Set, String)}, passing its own sort whitelist and default sort. Invalid
 *       params fail with {@code 400} via the existing {@code GlobalExceptionHandler} — no extra
 *       wiring.
 *   <li>Controller maps the page content to DTOs and returns {@link
 *       ec.twintailcs.electoral.shared.web.pagination.PagedResponse#of(org.springframework.data.domain.Page)
 *       PagedResponse.of(page.map(mapper::toDto))}.
 * </ol>
 *
 * <p>Small catalogs intentionally keep returning bare arrays; only high-volume listings adopt the
 * {@code {data, meta}} envelope.
 */
package ec.twintailcs.electoral.shared.web.pagination;
