import { TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { describe, it } from 'vitest';

import { expectA11yClean } from '../../testing/a11y-test.helper';
import { TableComponent } from './table.component';
import { TableColumn } from './table.model';

describe('TableComponent — accesibilidad (ODD 505, E.4)', () => {
  it('una tabla con columnas y filas no tiene violaciones', async () => {
    await TestBed.configureTestingModule({
      imports: [TableComponent],
      providers: [providePrimeNG()],
    }).compileComponents();

    const fixture = TestBed.createComponent(TableComponent);
    fixture.componentRef.setInput('columns', [
      { key: 'nombre', header: 'Nombre' },
      { key: 'orden', header: 'Orden' },
    ] as TableColumn<unknown>[]);
    fixture.componentRef.setInput('data', [
      { nombre: 'Presidente', orden: 1 },
      { nombre: 'Vicepresidente', orden: 2 },
    ]);
    fixture.detectChanges();

    await expectA11yClean(fixture.nativeElement as HTMLElement);
  });
});
