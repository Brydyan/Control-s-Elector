import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  parseScssBlocks,
  parseNestedScssRules,
  parseThemeTokens,
  ThemeResolver,
  ratioOf,
  relativeLuminance,
  parseColor,
  composite,
  contrastRatio,
} from './tokens.parser';

/**
 * S7/S8 of `odd/tasks/frontend-design-system-hardening.md`.
 *
 * The app declares its own colour layer in `src/theme/tokens.scss`, but the
 * `.app-dark` scheme did not exist: PrimeNG moved its half of the surface and
 * the app's own text stayed `#0f172a` on a `#0f172a` card. The pairs below are
 * the ones that were measured as unreadable in the running app, plus the
 * status palette that has to survive the change.
 *
 * The ratios are computed from the token values themselves (the `var()` chain
 * is resolved the same way the cascade resolves it), so the contract is
 * deterministic and does not need a browser.
 */

const THEME_DIR = dirname(fileURLToPath(import.meta.url));
const SOURCE_DIR = resolve(THEME_DIR, '..');

const tokensSource = readFileSync(join(THEME_DIR, 'tokens.scss'), 'utf8');
const typographySource = readFileSync(
  join(THEME_DIR, 'typography.scss'),
  'utf8',
);

const { light, dark } = parseThemeTokens(tokensSource);
const resolver = new ThemeResolver(light, dark);

/** WCAG 2.2 AA: 4.5:1 for body text, 3:1 for large text. */
const AA_TEXT = 4.5;
const AA_LARGE = 3;

interface ContrastPair {
  /** What the pair is, in the words of the defect report. */
  name: string;
  /** Token that paints the foreground. */
  fg: string;
  /** Token that paints the background. */
  bg: string;
  /** Surface under a translucent background. */
  over?: string;
  /** Minimum ratio the pair has to reach. */
  min: number;
}

const CARD_PAIRS: ContrastPair[] = [
  {
    name: 'card title on its card',
    fg: '--color-text-primary',
    bg: '--color-surface-card',
    min: AA_TEXT,
  },
  {
    name: 'form label on its card',
    fg: '--color-text-primary',
    bg: '--color-surface-card',
    min: AA_TEXT,
  },
  {
    name: 'candidate name on its card',
    fg: '--color-text-primary',
    bg: '--color-surface-card',
    min: AA_TEXT,
  },
  {
    name: 'acta detail value on its card',
    fg: '--color-text-primary',
    bg: '--color-surface-card',
    min: AA_TEXT,
  },
  {
    name: 'muted label on its card',
    fg: '--color-text-muted',
    bg: '--color-surface-card',
    min: AA_TEXT,
  },
  {
    name: 'secondary label on its card',
    fg: '--color-text-secondary',
    bg: '--color-surface-card',
    min: AA_TEXT,
  },
  {
    name: 'online status pill on the raised surface',
    fg: '--color-status-success',
    bg: '--color-surface-raised',
    min: AA_TEXT,
  },
  {
    name: 'sent stat value',
    fg: '--color-brand-primary',
    bg: '--color-surface-card',
    min: AA_LARGE,
  },
  {
    name: 'disputed stat value',
    fg: '--color-status-warning',
    bg: '--color-surface-card',
    min: AA_LARGE,
  },
  {
    name: 'corrected stat value',
    fg: '--color-status-corrected',
    bg: '--color-surface-card',
    min: AA_LARGE,
  },
  {
    name: 'approved stat value',
    fg: '--color-status-success',
    bg: '--color-surface-card',
    min: AA_LARGE,
  },
  {
    name: 'banner heading on the sidebar surface',
    fg: '--color-text-on-brand-panel',
    bg: '--color-surface-sidebar',
    min: AA_LARGE,
  },
];

const STATUS_PAIRS: ContrastPair[] = [
  {
    name: 'success status',
    fg: '--color-status-success',
    bg: '--color-status-success-bg',
    over: '--color-surface-card',
    min: AA_TEXT,
  },
  {
    name: 'warning status',
    fg: '--color-status-warning',
    bg: '--color-status-warning-bg',
    over: '--color-surface-card',
    min: AA_TEXT,
  },
  {
    name: 'danger status',
    fg: '--color-status-danger',
    bg: '--color-status-danger-bg',
    over: '--color-surface-card',
    min: AA_TEXT,
  },
  {
    name: 'info status',
    fg: '--color-status-info',
    bg: '--color-status-info-bg',
    over: '--color-surface-card',
    min: AA_TEXT,
  },
];

const PAIRS = [...CARD_PAIRS, ...STATUS_PAIRS];

describe('theme colour scheme', () => {
  it('should declare a dark scheme of its own', () => {
    expect(Object.keys(dark)).toContain('--color-surface-card');
    expect(Object.keys(dark)).toContain('--color-text-primary');
  });

  it('should paint the dark surfaces darker than the light ones', () => {
    const lightCard = parseColor(
      resolver.resolve('--color-surface-card', 'light'),
    );
    const darkCard = parseColor(
      resolver.resolve('--color-surface-card', 'dark'),
    );
    expect(lightCard).not.toBeNull();
    expect(darkCard).not.toBeNull();

    expect(relativeLuminance(darkCard!.rgb)).toBeLessThan(
      relativeLuminance(lightCard!.rgb),
    );
  });

  it('should keep the dark canvas below the card so the cards read as surfaces', () => {
    const canvas = parseColor(
      resolver.resolve('--color-surface-canvas', 'dark'),
    );
    const card = parseColor(resolver.resolve('--color-surface-card', 'dark'));
    expect(canvas).not.toBeNull();
    expect(card).not.toBeNull();

    expect(relativeLuminance(canvas!.rgb)).toBeLessThan(
      relativeLuminance(card!.rgb),
    );
  });

  it.each(PAIRS)(
    'should keep the $name readable in the light scheme',
    (pair) => {
      const ratio = ratioOf(resolver, 'light', pair.fg, pair.bg, pair.over);
      expect(ratio).toBeGreaterThanOrEqual(pair.min);
    },
  );

  it.each(PAIRS)(
    'should keep the $name readable in the dark scheme',
    (pair) => {
      const ratio = ratioOf(resolver, 'dark', pair.fg, pair.bg, pair.over);
      expect(ratio).toBeGreaterThanOrEqual(pair.min);
    },
  );
});

describe('global typography', () => {
  /**
   * The global rules forced a colour on every heading and paragraph, which
   * cancelled the colour of the coloured containers (the blue banner lost its
   * white text in the light scheme too). The colour has to be inherited.
   */
  const INHERITED_COLOUR_SELECTORS = ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p'];

  const blocks = parseScssBlocks(typographySource);

  it.each(INHERITED_COLOUR_SELECTORS)(
    'should not force a colour on the global %s rule',
    (selector) => {
      const offending = blocks.filter((block) => {
        const selectors = block.selector.split(',').map((part) => part.trim());
        if (!selectors.includes(selector)) return false;
        return /(^|[\s;{])color\s*:/.test(block.body);
      });

      expect(offending.map((block) => block.selector)).toEqual([]);
    },
  );

  it('should keep the colour on the typography utility classes', () => {
    for (const utility of ['.font-display', '.font-title', '.font-body']) {
      const declared = blocks.some(
        (block) =>
          block.selector
            .split(',')
            .map((part) => part.trim())
            .includes(utility) && /(^|[\s;{])color\s*:/.test(block.body),
      );
      expect(declared).toBe(true);
    }
  });
});

/**
 * The caption of the dashboard banner sat at 3.89:1 in the light scheme: the
 * rule painted it with `opacity: 0.8` on top of the brand blue, and no token
 * can undo an opacity. The caption has to carry a token for its colour and
 * reach AA (4.5:1) in both schemes.
 *
 * After the visual rebalance (S3 of `odd/tasks/frontend-visual-rebalance.md`)
 * the banner is no longer painted with the brand blue: it sits on
 * `--color-surface-sidebar` (slate-900 in the light scheme), so the
 * "background of the banner" the caption reads against is the sidebar token,
 * not the brand one. The rule on the caption stayed the same — white text,
 * no opacity — and now reads against a darker surface with even more head
 * room.
 */
describe('the dashboard banner caption', () => {
  const dashboardSource = readFileSync(
    join(SOURCE_DIR, 'app/features/dashboard/dashboard.page.scss'),
    'utf8',
  );

  const rules = parseNestedScssRules(dashboardSource);
  const caption = rules.find((rule) => rule.selector === '.banner-head p');

  it('should declare the caption with a colour token', () => {
    expect(caption).toBeDefined();

    const colorToken = caption!.body.match(
      /color\s*:\s*var\(\s*(--[\w-]+)\s*\)/,
    )?.[1];

    expect(
      colorToken,
      'the caption has to carry a colour token, not an opacity',
    ).toBeDefined();
  });

  it.each(['light', 'dark'] as const)(
    'should keep the caption readable in the %s scheme',
    (scheme) => {
      const colorToken = caption!.body.match(
        /color\s*:\s*var\(\s*(--[\w-]+)\s*\)/,
      )?.[1];
      expect(colorToken).toBeDefined();

      const opacity = Number(
        caption!.body.match(/opacity\s*:\s*([\d.]+)/)?.[1] ?? '1',
      );

      const foreground = parseColor(resolver.resolve(colorToken!, scheme));
      const background = parseColor(
        resolver.resolve('--color-surface-sidebar', scheme),
      );
      expect(foreground).not.toBeNull();
      expect(background).not.toBeNull();

      // What the browser paints: the colour of the token, dimmed by the
      // opacity of the rule, over the sidebar surface of the banner.
      const painted = composite(
        { ...foreground!, alpha: foreground!.alpha * opacity },
        background!.rgb,
      );

      expect(contrastRatio(painted, background!.rgb)).toBeGreaterThanOrEqual(
        AA_TEXT,
      );
    },
  );
});

/**
 * El placeholder de los inputs lo fija PrimeNG con
 *
 *     ::placeholder { color: color-mix(in oklab, currentcolor 50%, transparent) }
 *
 * y no expone ningun token `--p-*-placeholder`, asi que no se puede ajustar
 * desde el preset: hay que sobreescribir la pseudo-clase.
 *
 * Al 50% de alfa no llegaba a WCAG AA en ninguno de los dos esquemas. Medido
 * sobre las superficies reales de los inputs (`--color-surface-card`):
 *
 *     claro   50% de slate-900 sobre blanco  ->  3.41:1   falla
 *     oscuro  50% de slate-400 sobre slate-900 ->  2.69:1   falla
 *
 * El defecto no era un token mal elegido sino la opacidad: al 50%, ningun
 * gris de la rampa llega a 4.5:1 sobre su superficie, asi que cambiar de
 * token sin tocar el alfa tampoco lo habria arreglado.
 *
 * Este caso vivia fuera del alcance de esta suite — la de contraste ya
 * existe y calcula ratios de verdad, pero no miraba la pseudo-clase — y por
 * eso el defecto llego a `develop`. Queda cubierto para que una
 * actualizacion de PrimeNG no lo reintroduzca.
 */
describe('the input placeholder', () => {
  const PLACEHOLDER_MIN = AA_TEXT;

  it.each([
    ['light', 'the light scheme'],
    ['dark', 'the dark scheme'],
  ] as const)('should reach AA over the card surface in %s', (scheme) => {
    const ratio = ratioOf(
      resolver,
      scheme,
      '--color-text-muted',
      '--color-surface-card',
    );
    expect(ratio).toBeGreaterThanOrEqual(PLACEHOLDER_MIN);
  });

  it('should paint the placeholder with a theme token, not a fixed colour', () => {
    // Un color fijo passaria el ratio de un esquema y reprobria el otro.
    expect(tokensSource).toMatch(
      /^::placeholder\s*\{\s*color:\s*var\(--color-text-muted\);\s*\}/m,
    );
  });

  it('should not inherit the 50% alpha of the PrimeNG default', () => {
    // `color-mix(..., 50%, transparent)` es exactamente lo que produced el
    // defecto: el test anterior pasa solo si el alfa deja de ser 50%.
    // Solo la regla; el comentario de `tokens.scss` cita el default de
    // PrimeNG a proposito y no debe hacer fallar el test.
    const rule = tokensSource.match(/^::placeholder\s*\{[^}]*\}/m);
    expect(rule).not.toBeNull();
    expect(rule?.[0]).not.toMatch(/color-mix\([^)]*50%\s*,\s*transparent/);
  });
});
