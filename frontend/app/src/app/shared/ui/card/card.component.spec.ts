import { ComponentFixture, TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  parseThemeTokens,
  ThemeResolver,
} from '../../../../theme/tokens.parser';
import { CardComponent } from './card.component';

const THEME_DIR = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../theme',
);

const { light } = parseThemeTokens(
  readFileSync(join(THEME_DIR, 'tokens.scss'), 'utf8'),
);
const resolver = new ThemeResolver(light, light);

describe('CardComponent', () => {
  let component: CardComponent;
  let fixture: ComponentFixture<CardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CardComponent],
      providers: [providePrimeNG()],
    }).compileComponents();

    fixture = TestBed.createComponent(CardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display headerTitle when provided', () => {
    fixture.componentRef.setInput('headerTitle', 'Card Title');
    fixture.detectChanges();
    const titleEl = fixture.nativeElement.querySelector('.app-card__title');
    expect(titleEl).toBeTruthy();
    expect(titleEl.textContent).toContain('Card Title');
  });

  it('should not display header when no title or subtitle', () => {
    fixture.componentRef.setInput('headerTitle', undefined);
    fixture.componentRef.setInput('subtitle', undefined);
    fixture.detectChanges();
    const headerEl = fixture.nativeElement.querySelector('.app-card__header');
    expect(headerEl).toBeNull();
  });

  /*
   * S11: the card has no stylesheet of its own — the file that used to
   * declare one was dead, because the component never pointed at it. The
   * title is therefore an `h3` and the global scale sizes it: the card title
   * takes the title stop of the ladder (24px).
   */
  it('should render the title at the title stop of the global scale', () => {
    fixture.componentRef.setInput('headerTitle', 'Card Title');
    fixture.detectChanges();

    const title = fixture.nativeElement.querySelector('.app-card__title');

    expect(title.tagName).toBe('H3');
    expect(resolver.resolve('--font-size-title', 'light')).toBe('24px');
  });

  /* T8.S14: Configurable heading level for semantic HTML */

  it('should render h3 heading by default', () => {
    fixture.componentRef.setInput('headerTitle', 'Card Title');
    fixture.detectChanges();
    const h3 = fixture.nativeElement.querySelector('h3.app-card__title');
    expect(h3).toBeTruthy();
  });

  it('should render h2 heading when headingLevel is "h2"', () => {
    fixture.componentRef.setInput('headerTitle', 'Card Title');
    fixture.componentRef.setInput('headingLevel', 'h2');
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2.app-card__title');
    expect(h2).toBeTruthy();
  });

  it('should render h1 heading when headingLevel is "h1"', () => {
    fixture.componentRef.setInput('headerTitle', 'Card Title');
    fixture.componentRef.setInput('headingLevel', 'h1');
    fixture.detectChanges();
    const h1 = fixture.nativeElement.querySelector('h1.app-card__title');
    expect(h1).toBeTruthy();
  });

  it('should render h4 heading when headingLevel is "h4"', () => {
    fixture.componentRef.setInput('headerTitle', 'Card Title');
    fixture.componentRef.setInput('headingLevel', 'h4');
    fixture.detectChanges();
    const h4 = fixture.nativeElement.querySelector('h4.app-card__title');
    expect(h4).toBeTruthy();
  });
});
