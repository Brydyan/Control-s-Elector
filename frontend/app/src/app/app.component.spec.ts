import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { describe, expect, it } from 'vitest';

import { AppComponent } from './app.component';

describe('AppComponent', () => {
  it('should create the app', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([])],
    })
      .overrideComponent(AppComponent, {
        set: {
          imports: [],
          template: '<div></div>',
        },
      })
      .compileComponents();

    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('monta el toast en el root (ODD 505, A.3)', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), MessageService],
    }).compileComponents();

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    // El toast vive en el root: sin el, ningun aviso de ninguna pantalla
    // llegaria a verse.
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('app-toast'),
    ).toBeTruthy();
  });
});
