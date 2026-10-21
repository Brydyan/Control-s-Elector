import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync, statSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  parseScssBlocks,
  parseNestedScssRules,
  parseThemeTokens,
  ThemeResolver,
} from './tokens.parser';
import { electoralPreset } from './primeng-preset';

/**
 * S9 and S10 of `odd/tasks/frontend-design-system-hardening.md`.
 *
 * The app declared no typeface at all: `index.html` had no webfont, the
 * PrimeNG preset had no `fontFamily` token and the only `font-family` in the
 * app's own code was `inherit` plus two `monospace` declarations. Everything
 * rendered in the user agent's default serif, and the type scale had a tie:
 * `--font-size-2xl` (24px) was the same value as `--font-size-title`, so the
 * page title of three screens tied with the card title under it.
 *
 * The contracts below parse the stylesheets and resolve the `var()` chain the
 * same way the cascade resolves it, so they are deterministic and do not need
 * a browser.
 */

const THEME_DIR = dirname(fileURLToPath(import.meta.url));
const SOURCE_DIR = resolve(THEME_DIR, '..');

const tokensSource = readFileSync(join(THEME_DIR, 'tokens.scss'), 'utf8');
const typographySource = readFileSync(
  join(THEME_DIR, 'typography.scss'),
  'utf8',
);
const fontsPath = join(THEME_DIR, 'fonts.scss');
const fontsSource = existsSync(fontsPath)
  ? readFileSync(fontsPath, 'utf8')
  : '';
const indexHtml = readFileSync(join(SOURCE_DIR, 'index.html'), 'utf8');

const { light, dark } = parseThemeTokens(tokensSource);
const resolver = new ThemeResolver(light, dark);

/** The generic families a browser falls back to; none of them is a typeface. */
const GENERIC_FAMILIES = [
  'serif',
  'sans-serif',
  'monospace',
  'cursive',
  'fantasy',
  'system-ui',
  'ui-sans-serif',
  'ui-serif',
  'ui-monospace',
  'emoji',
  'math',
  'fangsong',
];

function resolveToken(name: string): string {
  return resolver.resolve(name, 'light');
}

/** The stacks are written across several lines; the comparison is not. */
function normalize(stack: string): string {
  return stack.replace(/\s+/g, ' ').trim();
}

/** The first family of a stack, with its quotes. */
function firstFamily(stack: string): string {
  return normalize(stack).split(',')[0].trim();
}

/* -------------------------------------------------------------------------- */
/* The typeface                                                                */
/* -------------------------------------------------------------------------- */

describe('the app typeface', () => {
  it('should declare a base family and a mono family', () => {
    expect(Object.keys(light)).toContain('--font-family-base');
    expect(Object.keys(light)).toContain('--font-family-mono');
  });

  it('should not fall back to the user agent default', () => {
    const stack = resolveToken('--font-family-base');
    const family = firstFamily(stack);

    expect(stack.length).toBeGreaterThan(0);
    expect(GENERIC_FAMILIES).not.toContain(family.toLowerCase());
    // A typeface, not a generic: the first family is a named face.
    expect(family).toMatch(/^["'].+["']$/);
  });

  it('should give the mono family its own face', () => {
    const stack = resolveToken('--font-family-mono');
    const family = firstFamily(stack);

    expect(GENERIC_FAMILIES).not.toContain(family.toLowerCase());
    expect(family).toMatch(/^["'].+["']$/);
  });

  it('should ship every font file it declares', () => {
    const urls = [...fontsSource.matchAll(/url\(["']?([^"')]+)["']?\)/g)].map(
      (match) => match[1],
    );

    expect(urls.length).toBeGreaterThan(0);

    for (const url of urls) {
      const file = resolve(SOURCE_DIR, url.replace(/^\//, ''));
      expect(existsSync(file), `${url} is declared but not shipped`).toBe(true);
      expect(statSync(file).size).toBeGreaterThan(0);
    }
  });

  it('should declare the faces with font-display swap', () => {
    const faces = fontsSource.split('@font-face').length - 1;

    expect(faces).toBeGreaterThan(0);
    expect(fontsSource).toMatch(/font-display:\s*swap/);
  });

  it('should declare a metric compatible fallback', () => {
    // A fallback that keeps the line box of the web font: the ascent, the
    // descent and the line gap are overridden with the metrics of the real
    // face, and the horizontal advance is scaled to match it.
    expect(fontsSource).toMatch(/ascent-override:/);
    expect(fontsSource).toMatch(/descent-override:/);
    expect(fontsSource).toMatch(/size-adjust:/);
  });

  it('should preload the font that blocks the first paint', () => {
    const preloads = [
      ...indexHtml.matchAll(/<link[^>]*rel="preload"[^>]*>/g),
    ].map((match) => match[0]);
    const fontPreloads = preloads.filter((link) => link.includes('as="font"'));

    expect(fontPreloads.length).toBeGreaterThan(0);

    for (const link of fontPreloads) {
      expect(link).toMatch(/type="font\/woff2"/);
      expect(link).toMatch(/crossorigin/);
      const href = link.match(/href="([^"]+)"/)?.[1];
      expect(href).toBeDefined();
      const file = resolve(SOURCE_DIR, href!.replace(/^\//, ''));
      expect(existsSync(file), `${href} is preloaded but not shipped`).toBe(
        true,
      );
    }
  });

  it('should preload exactly the file the stylesheet declares', () => {
    // The preloaded resource and the one the `@font-face` asks for have to
    // be the same URL, spelled the same way: with a relative href the
    // browser does not match them and downloads the font twice.
    const preload = indexHtml.match(
      /<link[^>]*rel="preload"[^>]*as="font"[^>]*>/,
    )?.[0];
    const href = preload?.match(/href="([^"]+)"/)?.[1];

    const declared = [
      ...fontsSource.matchAll(/url\(["']?([^"')]+)["']?\)/g),
    ].map((match) => match[1]);

    expect(href).toBeDefined();
    expect(declared).toContain(href);
  });
});

/* -------------------------------------------------------------------------- */
/* The applied stack                                                           */
/* -------------------------------------------------------------------------- */

describe('the applied stack', () => {
  const blocks = parseScssBlocks(typographySource);

  function ruleFor(selector: string) {
    return blocks.find((block) =>
      block.selector
        .split(',')
        .map((part) => part.trim())
        .includes(selector),
    );
  }

  it('should apply the base family on the body', () => {
    const body = ruleFor('body');

    expect(body).toBeDefined();
    expect(body!.body).toMatch(
      /font-family\s*:\s*var\(\s*--font-family-base\s*\)/,
    );
  });

  it('should not repeat the family on every element', () => {
    // Inheritance is the point: the body carries the face and the elements
    // below it inherit it.
    const declaring = blocks.filter((block) =>
      /(^|[\s;{])font-family\s*:/.test(block.body),
    );

    expect(declaring.map((block) => block.selector)).toEqual(['body']);
  });

  it('should point the PrimeNG preset at the same stack', () => {
    const semantic = electoralPreset.semantic as Record<string, unknown>;

    expect(normalize(String(semantic['fontFamily']))).toBe(
      normalize(resolveToken('--font-family-base')),
    );
  });
});

/* -------------------------------------------------------------------------- */
/* The ladder                                                                  */
/* -------------------------------------------------------------------------- */

describe('the type ladder', () => {
  function pixels(token: string): number {
    const value = resolveToken(token);
    const parsed = Number.parseFloat(value);
    expect(Number.isNaN(parsed), `${token} is not a length: ${value}`).toBe(
      false,
    );
    return parsed;
  }

  it('should be monotonic', () => {
    expect(pixels('--font-size-display')).toBeGreaterThan(
      pixels('--font-size-title'),
    );
    expect(pixels('--font-size-title')).toBeGreaterThan(
      pixels('--font-size-body'),
    );
    expect(pixels('--font-size-body')).toBeGreaterThan(
      pixels('--font-size-meta'),
    );
  });

  it('should not tie the legacy 2xl stop with the title stop', () => {
    // `--font-size-2xl` used to be 24px, the same value as the card title,
    // so the page title of three screens tied with the cards under it.
    expect(pixels('--font-size-2xl')).not.toBe(pixels('--font-size-title'));
  });

  it('should map every role to its stop', () => {
    const blocks = parseScssBlocks(typographySource);

    const roleOf = (selector: string): string | undefined => {
      for (const block of blocks) {
        const selectors = block.selector.split(',').map((part) => part.trim());
        if (!selectors.includes(selector)) continue;
        const token = block.body.match(
          /font-size\s*:\s*var\(\s*(--[\w-]+)\s*\)/,
        )?.[1];
        if (token) return token;
      }
      return undefined;
    };

    expect(roleOf('h1')).toBe('--font-size-display');
    expect(roleOf('h2')).toBe('--font-size-title');
    expect(roleOf('h3')).toBe('--font-size-title');
    expect(roleOf('p')).toBe('--font-size-body');
    expect(roleOf('small')).toBe('--font-size-meta');
  });

  it('should use every stop of the ladder at least once', () => {
    const stops = [
      '--font-size-display',
      '--font-size-title',
      '--font-size-body',
      '--font-size-meta',
    ];
    const sources = collectStylesheets();

    for (const stop of stops) {
      const used = sources.some((source) =>
        source.text.includes(`var(${stop})`),
      );
      expect(used, `${stop} is declared but never used`).toBe(true);
    }
  });
});

/* -------------------------------------------------------------------------- */
/* The figures                                                                 */
/* -------------------------------------------------------------------------- */

interface NumericSite {
  /** What the site is, in the words of the defect report. */
  name: string;
  /** The stylesheet that carries the rule, relative to `src/`. */
  scss: string;
  /** The selector of the rule that has to carry the tabular figures. */
  selector: string;
  /** The template that has to contain the site. */
  html?: string;
  /** A needle that proves the site exists in the template. */
  htmlNeedle?: string;
}

const NUMERIC_SITES: NumericSite[] = [
  {
    name: 'the inbox sufragantes and blancos/nulos cells',
    scss: 'app/features/coordinator/pages/actas-lista/actas-lista.page.scss',
    selector: '.td-numeric',
    html: 'app/features/coordinator/pages/actas-lista/actas-lista.page.html',
    htmlNeedle: 'td-numeric',
  },
  {
    name: 'the ballot vote column',
    scss: 'app/features/coordinator/pages/acta-detalle/acta-detalle.page.scss',
    selector: '.td-votos',
    html: 'app/features/coordinator/pages/acta-detalle/acta-detalle.page.html',
    htmlNeedle: 'td-votos',
  },
  {
    name: 'the observer vote inputs',
    scss: 'theme/typography.scss',
    selector: 'input[inputmode="numeric"]',
    html: 'app/features/observer/pages/registrar-acta/registrar-acta.page.html',
    htmlNeedle: 'inputmode="numeric"',
  },
  {
    name: 'the JRV figures',
    scss: 'app/features/observer/pages/mis-jrvs/mis-jrvs.page.scss',
    selector: '.jrv-stats strong',
    html: 'app/features/observer/pages/mis-jrvs/mis-jrvs.page.html',
    htmlNeedle: 'jrv-stats',
  },
];

function ruleMatches(rule: { selector: string }, selector: string): boolean {
  return rule.selector
    .split(',')
    .map((part) => part.trim())
    .some((part) => part === selector || part.endsWith(` ${selector}`));
}

describe('the electoral figures', () => {
  it.each(NUMERIC_SITES)('should give $name tabular figures', (site) => {
    const path = resolve(SOURCE_DIR, site.scss);
    expect(existsSync(path), `${site.scss} is missing`).toBe(true);

    const rules = parseNestedScssRules(readFileSync(path, 'utf8'));
    const matching = rules.filter((rule) => ruleMatches(rule, site.selector));

    expect(matching.length, `${site.selector} has no rule`).toBeGreaterThan(0);

    const tabular = matching.filter((rule) =>
      /font-variant-numeric\s*:\s*tabular-nums/.test(rule.body),
    );

    expect(
      tabular.length,
      `${site.selector} does not carry tabular figures`,
    ).toBeGreaterThan(0);
  });

  it.each(NUMERIC_SITES)('should find $name in the markup', (site) => {
    if (!site.html || !site.htmlNeedle) return;

    const path = resolve(SOURCE_DIR, site.html);
    expect(existsSync(path), `${site.html} is missing`).toBe(true);

    const html = readFileSync(path, 'utf8');
    expect(html).toContain(site.htmlNeedle);
  });

  it('should mark the numeric columns of the inbox table', () => {
    const html = readFileSync(
      resolve(
        SOURCE_DIR,
        'app/features/coordinator/pages/actas-lista/actas-lista.page.html',
      ),
      'utf8',
    );

    const header = html.match(/<ng-template #header>([\s\S]*?)<\/ng-template>/);
    const body = html.match(
      /<ng-template #body let-acta>([\s\S]*?)<\/ng-template>/,
    );

    expect(header).not.toBeNull();
    expect(body).not.toBeNull();

    const labels = [...header![1].matchAll(/<th\b[^>]*>([\s\S]*?)<\/th>/g)].map(
      (match) => match[1].trim(),
    );
    const cells = [...body![1].matchAll(/<td\b([^>]*)>/g)].map(
      (match) => match[1],
    );

    expect(labels.length).toBe(cells.length);

    for (const label of ['Sufragantes', 'Blancos / Nulos']) {
      const index = labels.indexOf(label);
      expect(index, `the ${label} column is not in the header`).toBeGreaterThan(
        -1,
      );
      expect(cells[index]).toContain('td-numeric');
    }
  });
});

/* -------------------------------------------------------------------------- */
/* One numeric voice                                                           */
/* -------------------------------------------------------------------------- */

describe('one numeric voice', () => {
  it('should not declare the monospace generic outside the token', () => {
    const offenders: string[] = [];

    for (const file of collectStylesheets()) {
      if (file.path.endsWith('theme/tokens.scss')) continue;

      const pattern = /(^|[;{\s])font-family\s*:\s*([^;}]*);/g;
      let match: RegExpExecArray | null;
      while ((match = pattern.exec(file.text)) !== null) {
        if (/\bmonospace\b/.test(match[2])) {
          offenders.push(`${file.path}: ${match[0].trim()}`);
        }
      }
    }

    expect(offenders).toEqual([]);
  });

  it('should reach the mono family through the token', () => {
    const actas = readFileSync(
      resolve(
        SOURCE_DIR,
        'app/features/coordinator/pages/actas-lista/actas-lista.page.scss',
      ),
      'utf8',
    );

    expect(actas).toMatch(/font-family\s*:\s*var\(\s*--font-family-mono\s*\)/);
  });
});

/* -------------------------------------------------------------------------- */
/* Helpers                                                                     */
/* -------------------------------------------------------------------------- */

interface Stylesheet {
  path: string;
  text: string;
}

function collectStylesheets(): Stylesheet[] {
  const files: Stylesheet[] = [];

  const walk = (directory: string) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const full = join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith('.scss')) {
        files.push({ path: full, text: readFileSync(full, 'utf8') });
      }
    }
  };

  walk(SOURCE_DIR);
  return files;
}
