import { ComponentFixture, TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseThemeTokens, ThemeResolver } from '../../../../theme/tokens.parser';
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
});
