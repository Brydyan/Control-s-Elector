/**
 * Shared pagination envelope for every paginated list endpoint.
 *
 * The backend answers high-volume listings with `{ data, meta }`.
 * Small catalogs intentionally keep returning bare arrays.
 *
 * Reuse in the next feature (5 lines each):
 *
 * Service:
 * ```ts
 * import type { PagedResponse } from '../../../shared/models/page.model';
 * getItems(page = PAGE_DEFAULTS.PAGE, size = PAGE_DEFAULTS.SIZE): Observable<PagedResponse<Item>> {
 *   const params = new HttpParams().set('page', page).set('size', size);
 *   return this.http.get<PagedResponse<Item>>(`${this.baseUrl}/items`, { params });
 * }
 * ```
 *
 * Page:
 * ```ts
 * import { PaginatorComponent } from '../../../shared/components/paginator/paginator.component';
 * // imports: [..., PaginatorComponent]
 * <app-paginator [page]="page" [size]="size" [totalElements]="total"
 *   (pageChange)="page = $event; load()" (sizeChange)="size = $event; page = 0; load()" />
 * ```
 */

export const SORT_DIR = {
  ASC: 'asc',
  DESC: 'desc',
} as const;

export type SortDir = (typeof SORT_DIR)[keyof typeof SORT_DIR];

export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PagedResponse<T> {
  data: T[];
  meta: PageMeta;
}

export const PAGE_DEFAULTS = {
  PAGE: 0,
  SIZE: 20,
} as const;
