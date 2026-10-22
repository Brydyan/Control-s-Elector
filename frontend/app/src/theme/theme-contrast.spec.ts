import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  parseScssBlocks,
  parseThemeTokens,
  ThemeResolver,
  ratioOf,
  relativeLuminance,
  parseColor,
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
    fg: '--text-primary',
    bg: '--color-surface-card',
    min: AA_TEXT,
  },
  {
    name: 'candidate name on its card',
    fg: '--text-primary',
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
    fg: '--primitive-purple-800',
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
    name: 'banner heading on the brand blue',
    fg: '--color-text-on-brand',
    bg: '--color-brand-primary',
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
