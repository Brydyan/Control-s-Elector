import { ComponentFixture, TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  parseThemeTokens,
  ThemeResolver,
  ratioOf,
} from '../../../../theme/tokens.parser';
import { KpiCardComponent, KpiTone } from './kpi-card.component';

/**
 * The card of an indicator.
 *
 * The component has two contracts and this suite covers the two:
 *
 * 1. The markup: a label, a figure and an optional line of support, plus the
 *    class of the tone.
 * 2. The colors: the figure of every tone has to be readable on the surface
 *    of the card in the two schemes. A tone that only reads in one scheme is
 *    a tone that breaks when the operator turns the light off.
 *
 * The second contract is computed from the tokens themselves, so it does not
 * need a browser and does not depend on the order the styles are loaded in.
 */

const THEME_DIR = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../theme',
);

const { light, dark } = parseThemeTokens(
  readFileSync(join(THEME_DIR, 'tokens.scss'), 'utf8'),
);
const resolver = new ThemeResolver(light, dark);

/** WCAG 2.2 AA: 3:1 for a large text, and a figure is a large text. */
const AA_LARGE = 3;

/** The five tones of the board and the tokens that paint each one. */
const TONES: Array<{ tone: KpiTone; figure: string; accent: string }> = [
  {
    tone: 'brand',
    figure: '--color-kpi-figure-brand',
    accent: '--color-kpi-accent-brand',
  },
  {
    tone: 'success',
    figure: '--color-kpi-figure-success',
    accent: '--color-kpi-accent-success',
  },
  {
    tone: 'warning',
    figure: '--color-kpi-figure-warning',
    accent: '--color-kpi-accent-warning',
  },
  {
    tone: 'corrected',
    figure: '--color-kpi-figure-corrected',
    accent: '--color-kpi-accent-corrected',
  },
  {
    tone: 'neutral',
    figure: '--color-kpi-figure-neutral',
    accent: '--color-kpi-accent-neutral',
  },
];

describe('KpiCardComponent', () => {
  let component: KpiCardComponent;
  let fixture: ComponentFixture<KpiCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KpiCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(KpiCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show the label, the figure and the hint', () => {
    fixture.componentRef.setInput('label', 'Enviadas');
    fixture.componentRef.setInput('value', 128);
    fixture.componentRef.setInput('hint', 'Pendientes revisión');
    fixture.detectChanges();

    const label = fixture.nativeElement.querySelector('.kpi-card__label');
    const figure = fixture.nativeElement.querySelector('.kpi-card__figure');
    const hint = fixture.nativeElement.querySelector('.kpi-card__hint');

    expect(label.textContent).toContain('Enviadas');
    expect(figure.textContent).toContain('128');
    expect(hint.textContent).toContain('Pendientes revisión');
  });

  it('should not show the hint when there is none', () => {
    fixture.componentRef.setInput('hint', undefined);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.kpi-card__hint')).toBeNull();
  });

  it('should show the icon when there is one', () => {
    fixture.componentRef.setInput('icon', 'pi pi-check');
    fixture.detectChanges();

    const icon = fixture.nativeElement.querySelector('.kpi-card__icon');
    expect(icon).toBeTruthy();
    expect(icon.classList).toContain('pi-check');
  });

  it('should not show the icon when there is none', () => {
    fixture.componentRef.setInput('icon', undefined);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.kpi-card__icon')).toBeNull();
  });

  it('should carry the class of its tone', () => {
    fixture.componentRef.setInput('tone', 'warning');
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('.kpi-card');
    expect(card.classList).toContain('kpi-card--warning');
  });

  it('should carry the class of the brand tone by default', () => {
    const card = fixture.nativeElement.querySelector('.kpi-card');
    expect(card.classList).toContain('kpi-card--brand');
  });

  it.each(TONES)('should declare the tokens of the $tone tone', (tone) => {
    expect(Object.keys(light)).toContain(tone.figure);
    expect(Object.keys(light)).toContain(tone.accent);
    expect(Object.keys(dark)).toContain(tone.figure);
    expect(Object.keys(dark)).toContain(tone.accent);
  });

  it.each(TONES)(
    'should keep the figure of the $tone tone readable in the light scheme',
    (tone) => {
      const ratio = ratioOf(
        resolver,
        'light',
        tone.figure,
        '--color-kpi-surface',
      );

      expect(ratio).toBeGreaterThanOrEqual(AA_LARGE);
    },
  );

  it.each(TONES)(
    'should keep the figure of the $tone tone readable in the dark scheme',
    (tone) => {
      const ratio = ratioOf(
        resolver,
        'dark',
        tone.figure,
        '--color-kpi-surface',
      );

      expect(ratio).toBeGreaterThanOrEqual(AA_LARGE);
    },
  );
});
