import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * T7.5 — the touch-targets contract test.
 *
 * S3 of `odd/tasks/frontend-responsive-shell.md`: 85 interactive elements
 * were below 44×44 CSS px, the WCAG 2.2 SC 2.5.8 (Target Size — Minimum, AA)
 * floor. The fix lifts the primaries globally, extends dense lists with a
 * transparent `::before` band, and puts the floor on the input itself where
 * a band over the row would steal the click.
 *
 * Why this suite reads SCSS instead of rendering: jsdom does not resolve the
 * full `var()` chain or layout SCSS the way a browser does, so any rendered
 * `getBoundingClientRect()` would understate (or zero) the real bounds and
 * pin the wrong contract. Reading the source and asserting shape is bounded,
 * deterministic and runs in milliseconds — the same approach
 * `theme-contrast.spec.ts` uses for its colour contracts.
 *
 * The source files tested are the ones T7.1 through T7.4 modified; the spec
 * names them so a future change that breaks the contract identifies exactly
 * which file moved the goalposts.
 */
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');

const read = (path: string): string =>
  readFileSync(join(ROOT, path), 'utf8');

const SOURCE = {
  base: read('src/theme/base.scss'),
  layout: read('src/app/layout/layout.scss'),
  acta: read(
    'src/app/features/observer/pages/registrar-acta/registrar-acta.page.scss',
  ),
  actasLista: read(
    'src/app/features/coordinator/pages/actas-lista/actas-lista.page.scss',
  ),
  actaDetalle: read(
    'src/app/features/coordinator/pages/acta-detalle/acta-detalle.page.scss',
  ),
  adminUsuarios: read(
    'src/app/features/admin/usuarios/admin-usuarios.page.scss',
  ),
  adminSesiones: read(
    'src/app/features/admin/sesiones/admin-sesiones.page.scss',
  ),
};

/** True when a CSS block body declares `name: value` at any position. */
function hasProp(body: string, name: string, value: string): boolean {
  const pattern = new RegExp(
    `(?:^|[\\s;{])${name}\\s*:\\s*${value.replace(
      /[.*+?^${}()|[\]\\]/g,
      '\\$&',
    )}(?:\\s|;|}|$)`,
  );
  return pattern.test(body);
}

/**
 * Returns the body of the first `selector { ... }` block whose selector matches
 * the given list (case-insensitive, anchored at line ends). Handles
 * comma-separated lists because SCSS allows them.
 */
function blockBody(source: string, selector: string): string | null {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  // The selector may be followed by other names in a comma list before the
  // opening brace, so we accept anything that is not `{` between selector
  // and brace.
  const pattern = new RegExp(
    `(?:^|\\b)(?:${escaped})\\b[^\\{]*\\{([^}]*)\\}`,
    'm',
  );
  const match = source.match(pattern);
  return match ? match[1] : null;
}

describe('T7.3 — global 44px floor for interactive controls (S3 palanca 1)', () => {
  const FLOOR_SELECTORS = [
    '.p-button',
    '.layout-topbar-action',
    '.p-paginator-first',
    '.p-paginator-prev',
    '.p-paginator-next',
    '.p-paginator-last',
  ];

  it.each(FLOOR_SELECTORS)(
    'should set min-height: 44px on %s (theme/base.scss)',
    (selector) => {
      const body = blockBody(SOURCE.base, selector);
      expect(
        body,
        `${selector} rule missing from theme/base.scss`,
      ).not.toBeNull();
      expect(hasProp(body!, 'min-height', '44px')).toBe(true);
    },
  );

  it.each(FLOOR_SELECTORS)(
    'should set min-width: 44px on %s when the control is icon-only',
    (selector) => {
      // .p-button is a labeled button and only needs min-height; the rest
      // are icon-only and carry both. The contract is: the source contains
      // `min-width: 44px` somewhere in a block whose selector list names
      // the icon-only control. We assert against the raw source because
      // each selector lives in a comma-list rule that may appear twice
      // (once for min-height, once for min-width), and `blockBody`
      // returns the first match only.
      if (selector === '.p-button') {
        // The base rule for `.p-button` is part of the min-height
        // selector list and does NOT carry min-width; that's the
        // documented contract for a labeled button.
        expect(SOURCE.base).not.toMatch(/\.p-button\b[^\{]*\{[^}]*min-width\s*:\s*44px/);
        return;
      }
      const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      const pattern = new RegExp(
        `(?:${escaped})\\b[^\\{]*\\{[^}]*min-width\\s*:\\s*44px`,
      );
      expect(
        SOURCE.base.match(pattern),
        `${selector} does not declare min-width: 44px in theme/base.scss`,
      ).not.toBeNull();
    },
  );

  it('should keep app-badge .p-tag at 44px only under the mobile breakpoint', () => {
    // The chip is decorative in WCAG 2.5.8 terms (no role, no tabindex);
    // the 44px floor is a phone-only gesture. The source must wrap the
    // rule in `@media (max-width: 767px)` so the desktop table row stays
    // compact.
    expect(SOURCE.base).toMatch(
      /@media\s*\(\s*max-width\s*:\s*767px\s*\)\s*\{[\s\S]*?app-badge\s+\.p-tag\s*\{[^}]*min-height\s*:\s*44px/,
    );
  });
});

describe('T7.1 — topbar drops to a single column under 768px (S1)', () => {
  it('should declare the topbar as flex-column inside @media (max-width: 767px)', () => {
    expect(SOURCE.layout).toMatch(
      /@media\s*\(\s*max-width\s*:\s*767px\s*\)\s*\{[\s\S]*?\.layout-topbar\s*\{[^}]*flex-direction\s*:\s*column/,
    );
  });
});

describe('T7.2 — acta capture fits at 390px (S2)', () => {
  it('should drop two-col grids to one column on the observer page', () => {
    // Mobile-first: the base rule is single-column (`1fr`) and the desktop
    // two-column layout is restored above 768px via `@media (min-width:
    // 768px)`. The two-col grid lives inside the BEM modifier
    // `&--2col` of `.form-grid` (so the literal `.form-grid--2col` does
    // not appear in the source — only the compiled output would show it).
    // We match the nested structure the source actually carries.
    expect(SOURCE.acta).toMatch(
      /\.form-grid\s*\{[\s\S]*?&--2col\s*\{[\s\S]*?grid-template-columns:\s*1fr[\s\S]*?@media\s*\(\s*min-width\s*:\s*768px[\s\S]*?1fr\s+1fr/,
    );
  });

  it('should keep the p-steps strip inside the page width', () => {
    expect(SOURCE.acta).toMatch(/p-steps[\s\S]*?\{[^}]*overflow-x\s*:\s*auto/);
  });

  it('should honour safe-area-inset-bottom on iOS', () => {
    expect(SOURCE.acta).toMatch(/safe-area-inset-bottom/);
  });
});

describe('T7.4 — hit-areas on dense lists (S3 palanca 2)', () => {
  it('should keep the .p-tab filter strip 44px tall on actas-lista', () => {
    const body = blockBody(SOURCE.actasLista, '.p-tab');
    expect(body, '.p-tab rule missing from actas-lista.page.scss').not.toBeNull();
    expect(hasProp(body!, 'min-height', '44px')).toBe(true);
  });

  it('should give the acta card on actas-lista a full-bleed touch band via ::before', () => {
    // The contract: a parent rule carries `position: relative` and its
    // nested `&::before` declares `inset: 0`. The band is invisible (no
    // background, no border) and extends the click target to the entire
    // card without moving any pixel of visual layout.
    const acts = SOURCE.actasLista;
    expect(
      acts,
      'expected a rule with position: relative followed by a ::before with inset: 0',
    ).toMatch(
      /[\s\S]*?position\s*:\s*relative[^}]*&\s*::before\s*\{[\s\S]*?inset\s*:\s*0/,
    );
  });

  it('should NOT paint a ::before over an input row in acta-detalle', () => {
    // Subagent rationale: a pseudo-element with `inset: 0` lives above
    // any descendant and steals the click. Where the row's target is the
    // input, the floor goes on the input itself.
    const ruleBlocks = SOURCE.actaDetalle.match(/&\s*::before\s*\{/g) ?? [];
    expect(
      ruleBlocks.length,
      'acta-detalle must not paint ::before over an input row',
    ).toBe(0);
  });

  it('should space the admin-usuarios action row with a flex gap', () => {
    // The contract: where the page repeats cards with a row of actions,
    // the actions are laid out as a flex with a gap so three ghost buttons
    // don't visually overlap when wrapped. The selector names change with
    // each refactor, so the assertion targets the structure (display:
    // flex + a `var(--space-2)` gap) rather than the exact selector.
    expect(SOURCE.adminUsuarios).toMatch(
      /\.actions\s*\{[\s\S]*?display\s*:\s*flex[\s\S]*?gap\s*:\s*var\(--space-2\)/,
    );
  });

  it('should not duplicate hit-areas in admin-sesiones (single-source-of-truth)', () => {
    // admin-sesiones imports admin-usuarios via @use, so the hit-area
    // rules live there. A redeclaration here would create two copies that
    // drift the next time either file moves.
    expect(SOURCE.adminSesiones).not.toMatch(/min-height\s*:\s*44px/);
  });
});
