import { NgTemplateOutlet } from '@angular/common';
import {
  Component,
  ContentChildren,
  Directive,
  EventEmitter,
  Input,
  Output,
  QueryList,
  TemplateRef,
  inject,
} from '@angular/core';
import { Menu } from 'primeng/menu';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { ButtonComponent } from '../button/button.component';
import { EmptyStateComponent } from '../empty-state/empty-state.component';
import {
  TableCellContext,
  TableColumn,
  TableRowActions,
  TableSortChange,
  TableSortOrder,
} from './table.model';

/**
 * The template of a custom cell.
 *
 * The directive is the bridge between the column contract of the table and the
 * markup of a cell: a page declares a template for a key and the table renders
 * it wherever that column appears —in the desktop table and in the mobile
 * card— so the same projection is never written twice.
 *
 * ```html
 * <app-table [data]="cargos()" [columns]="columns">
 *   <ng-template appTableCell="nombre" let-row>
 *     <strong>{{ row.nombre }}</strong>
 *   </ng-template>
 * </app-table>
 * ```
 */
@Directive({
  selector: 'ng-template[appTableCell]',
  standalone: true,
})
export class TableCellTemplate<T = unknown> {
  /** The key of the column the template renders. */
  @Input({ required: true }) appTableCell!: string;

  /** The template itself, ready to be instantiated by the table. */
  readonly templateRef = inject<TemplateRef<TableCellContext<T>>>(TemplateRef);
}

/**
 * The reusable table of the app.
 *
 * Every table of the app had the same shape —a header, a body, a row of
 * actions, an empty state and a card for the phone— and every page wrote it
 * again by hand, with its own classes and its own ways of being wrong. This
 * component is that shape, once:
 *
 * - The columns arrive as a contract (`TableColumn<T>`), not as markup.
 * - A cell that needs its own markup is projected with `appTableCell`.
 * - The row actions arrive as a function that returns the menu items.
 * - The loading state, the empty state and the mobile card are part of the
 *   component, so a page cannot forget one of them.
 *
 * The visual standard of the table is not here: it belongs to
 * `theme/tables.scss`, which owns the four pieces of a table —the header, the
 * row, the cell and the paginator— through the `app-table` class. This
 * component renders that class and does not restyle it, so the tables of the
 * app keep reading as one surface and the dark scheme keeps working, because
 * both schemes are the same tokens.
 */
@Component({
  selector: 'app-table',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    TableModule,
    Menu,
    Skeleton,
    ButtonComponent,
    EmptyStateComponent,
  ],
  templateUrl: './table.component.html',
  styleUrl: './table.component.scss',
})
export class TableComponent {
  /** The rows of the table. */
  @Input() data: any[] = [];

  /** The columns of the table. */
  @Input() columns: TableColumn<any>[] = [];

  /**
   * The actions of a row.
   *
   * When it is absent the table has no column of actions; when it is present
   * every row gets its own three-dot menu with the items the function
   * returns.
   */
  @Input() actions?: TableRowActions<any>;

  /** Whether the table is loading. */
  @Input() loading = false;

  /** The number of skeleton rows shown while the table loads. */
  @Input() skeletonRowCount = 4;

  /** The label of the three-dot menu of a row. */
  @Input() actionsLabel = 'Acciones';

  /** The label of the column of actions. */
  @Input() actionsHeader = 'Acciones';

  /** The icon of the empty state. */
  @Input() emptyIcon = 'pi pi-inbox';

  /** The title of the empty state. */
  @Input() emptyTitle = 'No hay datos';

  /** The description of the empty state. */
  @Input() emptyDescription?: string;

  /**
   * The key of the column that titles the mobile card.
   *
   * It is the first column when it is not declared.
   */
  @Input() mobileTitleColumn?: string;

  /** Whether the row under the pointer is highlighted. */
  @Input() rowHover = true;

  /** The function that identifies a row, used to track it in the mobile card. */
  @Input() trackBy?: (row: any, index: number) => string | number;

  /** Emitted when the order of the rows changes. */
  @Output() sortChange = new EventEmitter<TableSortChange<any>>();

  /** The templates of the custom cells, indexed by the key of their column. */
  @ContentChildren(TableCellTemplate)
  cellTemplates?: QueryList<TableCellTemplate<any>>;

  /** The column the table is sorted by. */
  sortKey: string | null = null;

  /** The order of the sort. */
  sortOrder: TableSortOrder = null;

  /** Whether the table has a column of actions. */
  get hasActions(): boolean {
    return typeof this.actions === 'function';
  }

  /** Whether there is at least one row to show. */
  get hasRows(): boolean {
    return (this.data?.length ?? 0) > 0;
  }

  /** The rows of the table, in the order the table shows them. */
  get sortedData(): any[] {
    const rows = this.data ?? [];

    if (!this.sortKey || !this.sortOrder) {
      return rows;
    }

    const column = this.columns.find((c) => c.key === this.sortKey);
    if (!column) {
      return rows;
    }

    const direction = this.sortOrder === 'asc' ? 1 : -1;

    return [...rows].sort(
      (a, b) =>
        direction *
        this.compare(this.valueOf(a, column), this.valueOf(b, column)),
    );
  }

  /** The column that titles the mobile card. */
  get titleColumn(): TableColumn<any> | undefined {
    const key = this.mobileTitleColumn ?? this.columns[0]?.key;
    return this.columns.find((c) => c.key === key);
  }

  /** The columns the mobile card shows below its title. */
  get mobileBodyColumns(): TableColumn<any>[] {
    const title = this.titleColumn;
    return this.columns.filter(
      (column) => !column.hideOnMobile && column.key !== title?.key,
    );
  }

  /** The rows of the skeleton. */
  get skeletonRows(): number[] {
    return Array.from(
      { length: Math.max(0, this.skeletonRowCount) },
      (_, i) => i,
    );
  }

  /**
   * Cycles the sort of a column: ascending, descending, and back to the order
   * the data arrived in.
   *
   * The table sorts its own rows instead of delegating it, because a column
   * can project its value out of the row —a derived name, a formatted
   * quantity— and the sort has to compare what the reader sees.
   */
  onSort(column: TableColumn<any>): void {
    if (!column.sortable) {
      return;
    }

    if (this.sortKey !== column.key) {
      this.sortKey = column.key;
      this.sortOrder = 'asc';
    } else if (this.sortOrder === 'asc') {
      this.sortOrder = 'desc';
    } else {
      this.sortKey = null;
      this.sortOrder = null;
    }

    this.sortChange.emit({ key: this.sortKey, order: this.sortOrder });
  }

  /** The template of a custom cell, if the page declared one for the column. */
  templateFor(key: string): TemplateRef<TableCellContext<any>> | null {
    const templates = this.cellTemplates;
    if (!templates) {
      return null;
    }

    return (
      templates.find((template) => template.appTableCell === key)
        ?.templateRef ?? null
    );
  }

  /** The context a projected cell template receives. */
  cellContext(
    row: any,
    column: TableColumn<any>,
    index: number,
  ): TableCellContext<any> {
    return { $implicit: row, row, column, index };
  }

  /** The value a cell shows when the page did not project it. */
  displayValue(row: any, column: TableColumn<any>): string {
    const value = this.valueOf(row, column);
    return value === null || value === undefined ? '' : String(value);
  }

  /** The value of a cell, as the column projects it. */
  valueOf(row: any, column: TableColumn<any>): unknown {
    return column.value ? column.value(row) : row?.[column.key];
  }

  /** The menu items of the three-dot menu of a row. */
  actionsFor(row: any) {
    return this.actions ? this.actions(row) : [];
  }

  /** The classes of the header cell of a column. */
  headerClasses(column: TableColumn<any>): string {
    return [
      'app-table__header-cell',
      column.sortable ? 'app-table__header-cell--sortable' : '',
      column.headerClass ?? '',
    ]
      .filter(Boolean)
      .join(' ');
  }

  /** The classes of the cell of a column. */
  cellClasses(column: TableColumn<any>): string {
    return ['app-table__cell', column.cellClass ?? '']
      .filter(Boolean)
      .join(' ');
  }

  /** The alignment of a column, as a CSS value. */
  alignStyle(column: TableColumn<any>): string | null {
    return column.align ?? null;
  }

  /** The sort state of a column, for the assistive technologies. */
  ariaSort(column: TableColumn<any>): string | null {
    if (!column.sortable) {
      return null;
    }

    if (this.sortKey !== column.key) {
      return 'none';
    }

    return this.sortOrder === 'asc' ? 'ascending' : 'descending';
  }

  /** The icon of the sort affordance of a column. */
  sortIconClasses(column: TableColumn<any>): string {
    const icon =
      this.sortKey !== column.key
        ? 'pi-sort-alt'
        : this.sortOrder === 'asc'
          ? 'pi-sort-up'
          : 'pi-sort-down';

    return `app-table__sort-icon pi ${icon}`;
  }

  /** The identity of a row in the mobile card view. */
  trackRow(index: number, row: any): string | number {
    return this.trackBy ? this.trackBy(row, index) : index;
  }

  /**
   * Compares two values of a column.
   *
   * Numbers compare as numbers and dates as dates. Everything else compares
   * as text, in Spanish and with the numbers inside the text read as
   * numbers —so "Puesto 9" comes before "Puesto 10"— because the columns of
   * the app carry labels as often as they carry figures.
   */
  private compare(a: unknown, b: unknown): number {
    if (a === b) {
      return 0;
    }

    if (a === null || a === undefined) {
      return -1;
    }

    if (b === null || b === undefined) {
      return 1;
    }

    if (typeof a === 'number' && typeof b === 'number') {
      return a - b;
    }

    if (a instanceof Date && b instanceof Date) {
      return a.getTime() - b.getTime();
    }

    return String(a).localeCompare(String(b), 'es', {
      numeric: true,
      sensitivity: 'base',
    });
  }
}
