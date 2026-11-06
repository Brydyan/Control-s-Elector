import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Paginator } from 'primeng/paginator';
import { PaginatorState } from 'primeng/types/paginator';
import { PAGE_DEFAULTS } from '../../models/page.model';

/**
 * Pagination control backed by PrimeNG.
 *
 * The public API is unchanged so the pages that consume it keep working: the
 * component owns the `p-paginator` and translates its events into the
 * `pageChange` and `sizeChange` outputs the pages already listen to.
 */
@Component({
  selector: 'app-paginator',
  standalone: true,
  imports: [Paginator],
  templateUrl: './paginator.component.html',
})
export class PaginatorComponent {
  @Input() page: number = PAGE_DEFAULTS.PAGE;
  @Input() size: number = PAGE_DEFAULTS.SIZE;
  @Input() totalElements: number = 0;
  @Input() pageSizes: number[] = [10, 20, 50];

  @Output() pageChange = new EventEmitter<number>();
  @Output() sizeChange = new EventEmitter<number>();

  get totalPages(): number {
    if (this.size <= 0) {
      return 0;
    }
    return Math.ceil(this.totalElements / this.size);
  }

  /**
   * PrimeNG's `first` is the index of the first record, not the page number:
   * the second page of twenty rows starts at index 20. The component's `page`
   * is a page number, so the two are translated here.
   */
  get firstRecordIndex(): number {
    return this.page * this.size;
  }

  /**
   * The paginator reports the page and the page size in the same event, so
   * the size change is translated here as well.
   */
  onPageChange(event: PaginatorState): void {
    const rows = event.rows ?? this.size;
    this.pageChange.emit(Math.floor((event.first ?? 0) / rows));
    if (rows !== this.size) {
      this.sizeChange.emit(rows);
    }
  }
}
