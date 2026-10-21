/**
 * Design token parser and WCAG contrast helpers.
 *
 * The tokens live in `src/theme/tokens.scss`, which is the single source of
 * truth for the app's own colour layer. The unit tests need to reason about
 * the *resolved* value of a token (a chain of `var()` references) in both
 * schemes, without booting a browser, so the SCSS is parsed here and the
 * chains are resolved the same way the cascade resolves them: the scheme
 * block wins over `:root`, and `:root` only supplies the scheme-invariant
 * values (the primitives, the scale and the radii).
 */

export type TokenMap = Record<string, string>;

export interface ScssBlock {
  selector: string;
  body: string;
}

export interface Rgba {
  rgb: [number, number, number];
  alpha: number;
}

/**
 * Prefixes of the *semantic* vocabulary: the tokens whose value depends on the
 * scheme. Every one of them has to be declared in both schemes, which is what
 * the token contract test asserts.
 */
const SEMANTIC_PREFIXES = [
  '--color-',
  '--text-',
  '--surface-',
  '--border-',
  '--sidebar-',
];

/** True when the token belongs to the semantic (scheme dependent) vocabulary. */
export function isSemanticToken(name: string): boolean {
  return SEMANTIC_PREFIXES.some((prefix) => name.startsWith(prefix));
}

/** Removes the SCSS comments so the parsers only see declarations. */
export function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/.*$/gm, '$1');
}

/** Parses the flat blocks of an SCSS file. */
export function parseScssBlocks(source: string): ScssBlock[] {
  const clean = stripComments(source);
  const blocks: ScssBlock[] = [];
  const pattern = /([^{}]+)\{([^{}]*)\}/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(clean)) !== null) {
    blocks.push({ selector: match[1].trim(), body: match[2] });
  }
  return blocks;
}

/** Extracts the custom property declarations of a block body. */
export function parseDeclarations(body: string): TokenMap {
  const declarations: TokenMap = {};
  const pattern = /(--[A-Za-z0-9_-]+)\s*:\s*([^;]+);/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(body)) !== null) {
    declarations[match[1]] = match[2].trim();
  }
  return declarations;
}

export interface ThemeTokens {
  /** The declarations of the `:root` block: the light scheme. */
  light: TokenMap;
  /** The declarations of the `.app-dark` block: the dark scheme. */
  dark: TokenMap;
}

/** Splits `tokens.scss` into the light (`:root`) and dark (`.app-dark`) layers. */
export function parseThemeTokens(source: string): ThemeTokens {
  const tokens: ThemeTokens = { light: {}, dark: {} };
  for (const block of parseScssBlocks(source)) {
    const declarations = parseDeclarations(block.body);
    if (block.selector === ':root') {
      Object.assign(tokens.light, declarations);
    } else if (block.selector === '.app-dark') {
      Object.assign(tokens.dark, declarations);
    }
  }
  return tokens;
}

/**
 * Resolves a token to its literal value, following the `var()` chain.
 *
 * In the dark scheme the `.app-dark` block wins. A semantic token that the
 * dark block does not declare is an error rather than a silent fallback to
 * the light value: that fallback is exactly the defect this contract guards.
 */
export class ThemeResolver {
  constructor(
    private readonly light: TokenMap,
    private readonly dark: TokenMap,
  ) {}

  private lookup(name: string, scheme: 'light' | 'dark'): string {
    const scope = scheme === 'dark' ? this.dark : this.light;
    const scoped = scope[name];
    if (scoped !== undefined) return scoped;
    if (scheme === 'dark' && isSemanticToken(name)) {
      throw new Error(`"${name}" is not declared in the dark scheme`);
    }
    const inherited = this.light[name];
    if (inherited === undefined) {
      throw new Error(`"${name}" is not declared`);
    }
    return inherited;
  }

  /** Resolves the literal value of a token in the given scheme. */
  resolve(name: string, scheme: 'light' | 'dark'): string {
    const scope = scheme === 'dark' ? this.dark : this.light;
    const seen = new Set<string>();
    let value = this.lookup(name, scheme);

    for (;;) {
      const reference = value.match(
        /var\(\s*(--[A-Za-z0-9_-]+)\s*(?:,[^)]*)?\)/,
      );
      if (!reference) break;
      const referenced = reference[1];
      if (seen.has(referenced)) {
        throw new Error(`circular reference on "${referenced}"`);
      }
      seen.add(referenced);
      const resolved = this.lookup(referenced, scheme);
      value = value.replace(reference[0], resolved);
    }

    void scope;
    return value.trim();
  }
}

/** Parses a `#rgb`, `#rrggbb` or `rgb()/rgba()` literal. */
export function parseColor(value: string): Rgba | null {
  const text = value.trim();

  const hex = text.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
  if (hex) {
    const digits =
      hex[1].length === 3
        ? hex[1]
            .split('')
            .map((digit) => digit + digit)
            .join('')
        : hex[1];
    return {
      rgb: [
        parseInt(digits.slice(0, 2), 16),
        parseInt(digits.slice(2, 4), 16),
        parseInt(digits.slice(4, 6), 16),
      ],
      alpha: 1,
    };
  }

  const functional = text.match(/rgba?\(([^)]+)\)/);
  if (functional) {
    const parts = functional[1]
      .split(/[,\s/]+/)
      .filter(Boolean)
      .map(Number);
    return {
      rgb: [parts[0], parts[1], parts[2]],
      alpha: parts.length > 3 ? parts[3] : 1,
    };
  }

  return null;
}

/** Composites a translucent colour over an opaque one. */
export function composite(
  foreground: Rgba,
  background: [number, number, number],
): [number, number, number] {
  return [0, 1, 2].map(
    (channel) =>
      foreground.rgb[channel] * foreground.alpha +
      background[channel] * (1 - foreground.alpha),
  ) as [number, number, number];
}

/** WCAG 2.x relative luminance. */
export function relativeLuminance(rgb: [number, number, number]): number {
  const [r, g, b] = rgb.map((channel) => {
    const value = channel / 255;
    return value <= 0.03928
      ? value / 12.92
      : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** WCAG 2.x contrast ratio between two opaque colours. */
export function contrastRatio(
  foreground: [number, number, number],
  background: [number, number, number],
): number {
  const a = relativeLuminance(foreground);
  const b = relativeLuminance(background);
  const lighter = Math.max(a, b);
  const darker = Math.min(a, b);
  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Resolves a colour pair and returns its contrast ratio.
 *
 * A translucent background is composited over `over` (the surface that holds
 * it), which is how the browser paints it.
 */
export function ratioOf(
  resolver: ThemeResolver,
  scheme: 'light' | 'dark',
  foregroundToken: string,
  backgroundToken: string,
  overToken?: string,
): number {
  const foreground = parseColor(
    resolver.resolve(foregroundToken, scheme),
  );
  if (!foreground) {
    throw new Error(`"${foregroundToken}" does not resolve to a colour`);
  }

  const background = parseColor(resolver.resolve(backgroundToken, scheme));
  if (!background) {
    throw new Error(`"${backgroundToken}" does not resolve to a colour`);
  }

  let base: [number, number, number] = [255, 255, 255];
  if (overToken) {
    const under = parseColor(resolver.resolve(overToken, scheme));
    if (!under) {
      throw new Error(`"${overToken}" does not resolve to a colour`);
    }
    base = composite(under, base);
  }

  const painted = composite(background, base);
  const text = composite(foreground, painted);
  return contrastRatio(text, painted);
}
