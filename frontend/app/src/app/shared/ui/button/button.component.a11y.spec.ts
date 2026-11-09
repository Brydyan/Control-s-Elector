import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { describe, expect, it } from 'vitest';

import {
  a11yViolations,
  expectA11yClean,
} from '../../testing/a11y-test.helper';
import { ButtonComponent } from './button.component';

/**
 * Un botón con texto: el nombre accesible es el texto.
 */
@Component({
  standalone: true,
  imports: [ButtonComponent],
  template: `<app-button>Guardar cambios</app-button>`,
})
class BotonConTextoComponent {}

/**
 * Un botón que solo tiene ícono: el nombre accesible tiene que venir del
 * `ariaLabel` del componente.
 */
@Component({
  standalone: true,
  imports: [ButtonComponent],
  template: `<app-button icon="pi pi-save" ariaLabel="Guardar cambios" />`,
})
class BotonSoloIconoComponent {}

/**
 * El mismo botón sin nombre: este es el contrapeso del gate. Si el analizador no
 * viera nada, este test pasaría igual y el "0 violaciones" de los otros no
 * probaría nada.
 */
@Component({
  standalone: true,
  imports: [ButtonComponent],
  template: `<app-button icon="pi pi-save" />`,
})
class BotonSinNombreComponent {}

describe('ButtonComponent — accesibilidad (ODD 505, E.4)', () => {
  const crear = async (component: unknown) => {
    await TestBed.configureTestingModule({
      imports: [component as never],
      providers: [providePrimeNG()],
    }).compileComponents();

    const fixture = TestBed.createComponent(component as never);
    fixture.detectChanges();
    return fixture;
  };

  it('un boton con texto no tiene violaciones', async () => {
    const fixture = await crear(BotonConTextoComponent);

    await expectA11yClean(fixture.nativeElement as HTMLElement);
  });

  it('un boton de solo icono con aria-label no tiene violaciones', async () => {
    const fixture = await crear(BotonSoloIconoComponent);

    await expectA11yClean(fixture.nativeElement as HTMLElement);

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'button',
    );
    expect(button?.getAttribute('aria-label')).toBe('Guardar cambios');
  });

  it('un boton de solo icono sin nombre accesible SI se reporta', async () => {
    const fixture = await crear(BotonSinNombreComponent);

    const violations = await a11yViolations(
      fixture.nativeElement as HTMLElement,
    );

    expect(violations).toContain('button-name');
  });
});
