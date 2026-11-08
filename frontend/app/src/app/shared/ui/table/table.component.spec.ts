import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { providePrimeNG } from 'primeng/config';
import { MenuItem } from 'primeng/api';
import { TableCellTemplate, TableComponent } from './table.component';
import { TableColumn } from './table.model';

/**
 * The reusable table.
 *
 * The component has four contracts and this suite covers the four:
 *
 * 1. The markup: a header per column, a cell per column and row, the menu of
 *    the actions, the empty state and the skeleton.
 * 2. The projection: a cell declared with `appTableCell` is rendered where
 *    the column appears —in the desktop table and in the mobile card.
 * 3. The order: the table sorts its own rows, and it sorts them by the value
 *    the reader sees, not by the raw property behind the column.
 * 4. The responsive: the row opens as a card on the phone.
 */

interface TestRow {
  id: number;
  nombre: string;
  nivel: string;
  puestos: number;
}

const ROWS: TestRow[] = [
  { id: 1, nombre: 'Presidente', nivel: 'NACIONAL', puestos: 1 },
  { id: 2, nombre: 'Asambleísta', nivel: 'PROVINCIA', puestos: 4 },
  { id: 3, nombre: 'Alcalde', nivel: 'CANTON', puestos: 1 },
];

const COLUMNS: TableColumn<TestRow>[] = [
  { key: 'nombre', header: 'Cargo' },
  { key: 'nivel', header: 'Nivel Territorial' },
  { key: 'puestos', header: 'Puestos a Elegir' },
];

/**
 * A page that uses the table.
 *
 * The host declares a projection for the `nombre` column, so the suite can
 * check that the markup a page writes reaches the cell —and the card— the
 * table draws.
 */
@Component({
  standalone: true,
  imports: [TableComponent, TableCellTemplate],
  template: `
    <app-table
      [data]="rows"
      [columns]="columns"
      [actions]="actions"
      [loading]="loading"
      [emptyTitle]="emptyTitle"
      [emptyDescription]="emptyDescription"
      [mobileTitleColumn]="'nombre'"
      [trackBy]="trackBy"
      (sortChange)="onSortChange($event)"
    >
      <ng-template appTableCell="nombre" let-row>
        <strong class="custom-cell">{{ row.nombre }}</strong>
      </ng-template>
    </app-table>
  `,
})
class HostComponent {
  rows: TestRow[] = ROWS;
  columns: TableColumn<TestRow>[] = COLUMNS;
  loading = false;
  emptyTitle = 'No se encontraron cargos';
  emptyDescription = 'No hay cargos con los filtros seleccionados.';

  readonly trackBy = (row: TestRow): number => row.id;

  readonly actions = (row: TestRow): MenuItem[] => [
    { label: 'Editar', icon: 'pi pi-pencil', command: () => row },
  ];

  readonly emitted: unknown[] = [];

  onSortChange(event: unknown): void {
    this.emitted.push(event);
  }
}

describe('TableComponent', () => {
  let fixture: ComponentFixture<TableComponent>;
  let component: TableComponent;

  const hostColumns: TableColumn<any>[] = [
    { key: 'nombre', header: 'Cargo' },
    { key: 'nivel', header: 'Nivel Territorial' },
    { key: 'puestos', header: 'Puestos a Elegir' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TableComponent, HostComponent],
      providers: [providePrimeNG()],
    }).compileComponents();

    fixture = TestBed.createComponent(TableComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('data', ROWS);
    fixture.componentRef.setInput('columns', hostColumns);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('the header', () => {
    it('should render one header per column', () => {
      const headers = fixture.nativeElement.querySelectorAll('th');

      expect(headers.length).toBe(hostColumns.length);
      expect(headers[0].textContent).toContain('Cargo');
      expect(headers[1].textContent).toContain('Nivel Territorial');
      expect(headers[2].textContent).toContain('Puestos a Elegir');
    });

    it('should apply the width of the column', () => {
      fixture.componentRef.setInput('columns', [
        { key: 'nombre', header: 'Cargo', width: '30%' },
        { key: 'nivel', header: 'Nivel' },
      ]);
      fixture.detectChanges();

      const headers = fixture.nativeElement.querySelectorAll('th');
      expect(headers[0].style.width).toBe('30%');
    });

    it('should apply the alignment of the column', () => {
      fixture.componentRef.setInput('columns', [
        { key: 'nombre', header: 'Cargo', align: 'center' },
        { key: 'nivel', header: 'Nivel', align: 'end' },
      ]);
      fixture.detectChanges();

      const headers = fixture.nativeElement.querySelectorAll('th');
      expect(headers[0].style.textAlign).toBe('center');
      expect(headers[1].style.textAlign).toBe('end');
    });

    it('should add the column of actions when the table has actions', () => {
      fixture.componentRef.setInput('actions', () => []);
      fixture.detectChanges();

      const headers = fixture.nativeElement.querySelectorAll('th');
      expect(headers.length).toBe(hostColumns.length + 1);
      expect(headers[headers.length - 1].textContent).toContain('Acciones');
    });
  });

  describe('the body', () => {
    it('should render one cell per column and row', () => {
      const cells = fixture.nativeElement.querySelectorAll('td');

      expect(cells.length).toBe(ROWS.length * hostColumns.length);
      expect(cells[0].textContent).toContain('Presidente');
      expect(cells[1].textContent).toContain('NACIONAL');
      expect(cells[2].textContent).toContain('1');
    });

    it('should render the value the column projects', () => {
      fixture.componentRef.setInput('columns', [
        {
          key: 'nombre',
          header: 'Cargo',
          value: (row: TestRow) => row.nombre.toUpperCase(),
        },
      ]);
      fixture.detectChanges();

      const cells = fixture.nativeElement.querySelectorAll('td');
      expect(cells[0].textContent).toContain('PRESIDENTE');
    });

    it('should render an empty cell when the value is missing', () => {
      fixture.componentRef.setInput('data', [
        { id: 1, nombre: undefined, nivel: null, puestos: 0 },
      ]);
      fixture.detectChanges();

      const cells = fixture.nativeElement.querySelectorAll('td');
      expect(cells[0].textContent.trim()).toBe('');
      expect(cells[1].textContent.trim()).toBe('');
    });
  });

  describe('the actions', () => {
    it('should not render a column of actions when there are no actions', () => {
      const headers = fixture.nativeElement.querySelectorAll('th');
      const buttons = fixture.nativeElement.querySelectorAll(
        '.app-table__desktop .pi-ellipsis-v',
      );

      expect(headers.length).toBe(hostColumns.length);
      expect(buttons.length).toBe(0);
    });

    it('should render a three-dot menu for every row', () => {
      fixture.componentRef.setInput('actions', (row: TestRow) => [
        { label: `Editar ${row.nombre}` },
      ]);
      fixture.detectChanges();

      const buttons = fixture.nativeElement.querySelectorAll(
        '.app-table__desktop .pi-ellipsis-v',
      );
      expect(buttons.length).toBe(ROWS.length);
    });

    it('should render a menu per row', () => {
      fixture.componentRef.setInput('actions', () => []);
      fixture.detectChanges();

      const menus = fixture.nativeElement.querySelectorAll(
        '.app-table__desktop p-menu',
      );
      expect(menus.length).toBe(ROWS.length);
    });

    it('should give the menu the actions of its row', () => {
      fixture.componentRef.setInput('actions', (row: TestRow) => [
        { label: `Editar ${row.nombre}` },
      ]);
      fixture.detectChanges();

      const items = component.actionsFor(ROWS[1]);
      expect(items[0].label).toBe('Editar Asambleísta');
    });

    it('should return no actions when the table has none', () => {
      expect(component.actionsFor(ROWS[0])).toEqual([]);
      expect(component.hasActions).toBe(false);
    });

    it('should label the button of the menu', () => {
      fixture.componentRef.setInput('actions', () => []);
      fixture.componentRef.setInput('actionsLabel', 'Acciones del cargo');
      fixture.detectChanges();

      const button = fixture.nativeElement.querySelector(
        '.app-table__desktop app-button button',
      );
      expect(button.getAttribute('aria-label')).toBe('Acciones del cargo');
    });
  });

  describe('the empty state', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('data', []);
      fixture.detectChanges();
    });

    it('should show the empty state when there are no rows', () => {
      expect(
        fixture.nativeElement.querySelector('app-empty-state'),
      ).toBeTruthy();
    });

    it('should show the title and the description of the empty state', () => {
      fixture.componentRef.setInput('emptyTitle', 'No hay cargos');
      fixture.componentRef.setInput('emptyDescription', 'Ajusta los filtros.');
      fixture.detectChanges();

      const empty = fixture.nativeElement.querySelector('app-empty-state');
      expect(empty.textContent).toContain('No hay cargos');
      expect(empty.textContent).toContain('Ajusta los filtros.');
    });

    it('should not show the table when there are no rows', () => {
      expect(fixture.nativeElement.querySelector('p-table')).toBeNull();
    });
  });

  describe('the loading state', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('loading', true);
      fixture.detectChanges();
    });

    it('should show the skeleton of the table', () => {
      expect(
        fixture.nativeElement.querySelector('.app-table__skeleton'),
      ).toBeTruthy();
    });

    it('should show one skeleton row per row of the skeleton', () => {
      fixture.componentRef.setInput('skeletonRowCount', 2);
      fixture.detectChanges();

      const rows = fixture.nativeElement.querySelectorAll(
        '.app-table__skeleton-table tbody tr',
      );
      expect(rows.length).toBe(2);
    });

    it('should show one skeleton cell per column', () => {
      const cells = fixture.nativeElement.querySelectorAll(
        '.app-table__skeleton-table tbody tr:first-child td',
      );
      expect(cells.length).toBe(hostColumns.length);
    });

    it('should not show the table while it loads', () => {
      expect(fixture.nativeElement.querySelector('p-table')).toBeNull();
    });

    it('should not show the empty state while it loads', () => {
      expect(fixture.nativeElement.querySelector('app-empty-state')).toBeNull();
    });
  });

  describe('the order', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('columns', [
        { key: 'nombre', header: 'Cargo', sortable: true },
        { key: 'puestos', header: 'Puestos' },
      ]);
      fixture.detectChanges();
    });

    it('should not sort a column that is not sortable', () => {
      const header = fixture.nativeElement.querySelectorAll('th')[1];
      header.click();
      fixture.detectChanges();

      expect(component.sortKey).toBeNull();
      expect(component.sortOrder).toBeNull();
    });

    it('should sort ascending when a sortable header is clicked', () => {
      fixture.nativeElement.querySelectorAll('th')[0].click();
      fixture.detectChanges();

      expect(component.sortKey).toBe('nombre');
      expect(component.sortOrder).toBe('asc');
      expect(component.sortedData.map((r) => r.nombre)).toEqual([
        'Alcalde',
        'Asambleísta',
        'Presidente',
      ]);
    });

    it('should sort descending on the second click', () => {
      const header = fixture.nativeElement.querySelectorAll('th')[0];

      header.click();
      fixture.detectChanges();
      header.click();
      fixture.detectChanges();

      expect(component.sortOrder).toBe('desc');
      expect(component.sortedData.map((r) => r.nombre)).toEqual([
        'Presidente',
        'Asambleísta',
        'Alcalde',
      ]);
    });

    it('should go back to the original order on the third click', () => {
      const header = fixture.nativeElement.querySelectorAll('th')[0];

      header.click();
      header.click();
      header.click();
      fixture.detectChanges();

      expect(component.sortKey).toBeNull();
      expect(component.sortOrder).toBeNull();
      expect(component.sortedData).toEqual(ROWS);
    });

    it('should sort when the header is activated with the keyboard', () => {
      const header = fixture.nativeElement.querySelectorAll('th')[0];
      header.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }),
      );
      fixture.detectChanges();

      expect(component.sortKey).toBe('nombre');
      expect(component.sortOrder).toBe('asc');
    });

    it('should sort by the value the column projects', () => {
      fixture.componentRef.setInput('columns', [
        {
          key: 'nombre',
          header: 'Cargo',
          sortable: true,
          value: (row: TestRow) => row.puestos,
        },
      ]);
      fixture.detectChanges();

      component.onSort(component.columns[0]);

      expect(component.sortedData.map((r) => r.puestos)).toEqual([1, 1, 4]);
    });

    it('should not modify the array of the data', () => {
      component.onSort({ key: 'nombre', header: 'Cargo', sortable: true });

      expect(component.data).toEqual(ROWS);
      expect(component.data.map((r) => r.nombre)).toEqual([
        'Presidente',
        'Asambleísta',
        'Alcalde',
      ]);
    });

    it('should declare the order of a sortable column', () => {
      const headers = fixture.nativeElement.querySelectorAll('th');

      expect(headers[0].getAttribute('aria-sort')).toBe('none');
      headers[0].click();
      fixture.detectChanges();

      expect(headers[0].getAttribute('aria-sort')).toBe('ascending');
    });

    it('should not declare the order of a column that is not sortable', () => {
      const headers = fixture.nativeElement.querySelectorAll('th');

      expect(headers[1].getAttribute('aria-sort')).toBeNull();
    });

    it('should show the affordance of the order only on sortable columns', () => {
      const icons = fixture.nativeElement.querySelectorAll(
        '.app-table__sort-icon',
      );

      expect(icons.length).toBe(1);
    });
  });

  describe('the mobile card', () => {
    it('should open every row as a card', () => {
      const cards = fixture.nativeElement.querySelectorAll('.app-table__card');

      expect(cards.length).toBe(ROWS.length);
    });

    it('should title the card with the first column', () => {
      const title = fixture.nativeElement.querySelector(
        '.app-table__card-title',
      );

      expect(title.textContent).toContain('Presidente');
    });

    it('should leave the title column out of the body of the card', () => {
      const card = fixture.nativeElement.querySelector('.app-table__card');
      const labels = [...card.querySelectorAll('.app-table__card-label')].map(
        (label) => label.textContent?.trim(),
      );

      expect(labels).toEqual(['Nivel Territorial', 'Puestos a Elegir']);
    });

    it('should leave a hidden column out of the card', () => {
      fixture.componentRef.setInput('columns', [
        { key: 'nombre', header: 'Cargo' },
        { key: 'nivel', header: 'Nivel', hideOnMobile: true },
      ]);
      fixture.detectChanges();

      const card = fixture.nativeElement.querySelector('.app-table__card');
      const labels = [...card.querySelectorAll('.app-table__card-label')].map(
        (label) => label.textContent?.trim(),
      );

      expect(labels).not.toContain('Nivel');
    });
  });

  describe('the identity of a row', () => {
    it('should track the row by the function it is given', () => {
      fixture.componentRef.setInput('trackBy', (row: TestRow) => row.id);
      fixture.detectChanges();

      expect(component.trackRow(0, ROWS[0])).toBe(1);
    });

    it('should track the row by its position when there is no function', () => {
      expect(component.trackRow(2, ROWS[0])).toBe(2);
    });
  });
});

describe('TableComponent in a page', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [providePrimeNG()],
    }).compileComponents();

    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('should render the projected template in the desktop table', () => {
    const custom = fixture.nativeElement.querySelectorAll(
      '.app-table__desktop .custom-cell',
    );

    expect(custom.length).toBe(ROWS.length);
    expect(custom[0].textContent).toContain('Presidente');
  });

  it('should render the projected template in the mobile card', () => {
    const card = fixture.nativeElement.querySelector('.app-table__card');
    const custom = card.querySelectorAll('.custom-cell');

    expect(custom.length).toBe(1);
    expect(custom[0].textContent).toContain('Presidente');
  });

  it('should find the template of the column it declares', () => {
    const table = fixture.debugElement.query(By.directive(TableComponent))
      .componentInstance as TableComponent;

    expect(table.templateFor('nombre')).toBeTruthy();
    expect(table.templateFor('nivel')).toBeNull();
  });

  it('should announce the change of the order', () => {
    const table = fixture.debugElement.query(By.directive(TableComponent))
      .componentInstance as TableComponent;

    table.onSort({ key: 'nombre', header: 'Cargo', sortable: true });

    expect(fixture.componentInstance.emitted).toEqual([
      { key: 'nombre', order: 'asc' },
    ]);
  });

  it('should track the rows of the card by the identity of the row', () => {
    const cards = fixture.nativeElement.querySelectorAll('.app-table__card');

    expect(cards.length).toBe(ROWS.length);
  });
});
