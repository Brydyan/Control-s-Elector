import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { describe, expect, it } from 'vitest';

import { ToastComponent } from './toast.component';

describe('ToastComponent (ODD 505, A.1)', () => {
  it('monta el toast de PrimeNG en la esquina superior derecha', async () => {
    await TestBed.configureTestingModule({
      imports: [ToastComponent],
      providers: [providePrimeNG(), MessageService],
    }).compileComponents();

    const fixture = TestBed.createComponent(ToastComponent);
    fixture.detectChanges();

    const toast = (fixture.nativeElement as HTMLElement).querySelector(
      'p-toast',
    );
    expect(toast).toBeTruthy();
  });
});
