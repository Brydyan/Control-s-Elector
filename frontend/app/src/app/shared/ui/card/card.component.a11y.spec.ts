import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { describe, it } from 'vitest';

import { expectA11yClean } from '../../testing/a11y-test.helper';
import { CardComponent } from './card.component';

@Component({
  standalone: true,
  imports: [CardComponent],
  template: `<app-card headerTitle="Cargos" subtitle="Catalogo electoral" />`,
})
class CardHostComponent {}

describe('CardComponent — accesibilidad (ODD 505, E.4)', () => {
  it('una card con titulo no tiene violaciones', async () => {
    await TestBed.configureTestingModule({
      imports: [CardHostComponent],
      providers: [providePrimeNG()],
    }).compileComponents();

    const fixture = TestBed.createComponent(CardHostComponent);
    fixture.detectChanges();

    await expectA11yClean(fixture.nativeElement as HTMLElement);
  });
});
