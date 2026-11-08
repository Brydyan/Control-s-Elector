import { MenuItem } from 'primeng/api';

/**
 * The alignment of the content of a cell.
 *
 * `start` and `end` are logical, not physical: they follow the writing
 * direction, so the same column contract works in a left-to-right and in a
 * right-to-left locale.
 */
export type TableAlign = 'start' | 'center' | 'end';

/**
 * The order a column is sorted in.
 *
 * `null` is the third state of the cycle: the column is not the sort key, so
 * the table is back to the order the data arrived in.
 */
export type TableSortOrder = 'asc' | 'desc' | null;

/**
 * A column of the table.
 *
 * The column is the contract of the table: the page says which columns it has
 * and what each one means, and the component turns that contract into the
 * header, the cells, the mobile card and the sort. The page does not repeat
 * the markup of a table it already described.
 */
export interface TableColumn<T> {
  /** The property of the row the column shows. */
  key: keyof T & string;

  /** The label of the column. */
  header: string;

  /** The width of the column, as any CSS length. */
  width?: string;

  /** Whether the column can be sorted by the table. */
  sortable?: boolean;

  /** The alignment of the header and of the cells of the column. */
  align?: TableAlign;

  /**
   * Projects the value of the cell out of the row.
   *
   * When it is absent the cell shows `row[key]`. When it is present the
   * projection is also what the sort compares, so a column that renames,
   * formats or derives its value sorts by that value and not by the raw
   * property behind it.
   */
  value?: (row: T) => unknown;

  /** Extra classes for the cells of the column. */
  cellClass?: string;

  /** Extra classes for the header cell of the column. */
  headerClass?: string;

  /** Whether the column is left out of the mobile card view. */
  hideOnMobile?: boolean;
}

/**
 * The context a projected cell template receives.
 *
 * The row is the implicit context, so a template reads `{{ row.nombre }}`, and
 * it is also named, so a template that needs the column or the position of the
 * row can ask for them.
 */
export interface TableCellContext<T> {
  /** The row the cell belongs to. */
  $implicit: T;

  /** The row the cell belongs to. */
  row: T;

  /** The column the cell belongs to. */
  column: TableColumn<T>;

  /** The position of the row inside the table. */
  index: number;
}

/**
 * The event the table emits when the sort changes.
 *
 * The table sorts its own rows, so the event is a notification and not a
 * request: a page that paginates on the server can read the new order from
 * it.
 */
export interface TableSortChange<T> {
  /** The column the table is sorted by, or `null` when it is not sorted. */
  key: (keyof T & string) | null;

  /** The order of the sort. */
  order: TableSortOrder;
}

/**
 * The actions of a row, as the menu items of its three-dot menu.
 *
 * The function receives the row, so the page decides what a row can do —edit,
 * delete, review— and the table only draws the menu.
 */
export type TableRowActions<T> = (row: T) => MenuItem[];
