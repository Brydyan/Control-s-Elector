#!/usr/bin/env node
/**
 * The token guard of the app.
 *
 * S11 of `odd/tasks/frontend-design-system-hardening.md`: the token contract
 * was unenforceable because nothing checked it. This script is the check, and
 * `src/theme/token-guard.spec.ts` is the proof that it fails when it should.
 *
 * Three rules:
 *
 * 1. **No undeclared reference.** Every `var(--x)` in the app's SCSS, HTML and
 *    TS has to be declared by the theme layer (`src/theme/**`) or published by
 *    PrimeNG (`--p-*`, emitted at runtime by the styled engine). A reference
 *    that nothing declares does not fail loudly: the declaration is dropped at
 *    computed-value time, so a shorthand (`border-left: 4px solid
 *    var(--missing)`) loses the whole declaration and the rule paints nothing.
 *    That is how the checksum banner lost its container.
 *
 * 2. **No raw colour outside the theme layer.** A colour that is not a token
 *    is a colour the design system does not know about: it does not follow the
 *    scheme and nobody can change it. The only exceptions are the ones listed
 *    in `RAW_COLOUR_ALLOWLIST`, each with the reason it is intentional.
 *
 * 3. **No mixed vocabulary.** The app owns its vocabulary and the PrimeNG
 *    layer is fed from it, so a file reads either the app's names or
 *    PrimeNG's, never both. The bridge (`src/theme/primeng-preset.ts`) is the
 *    one file allowed to name both.
 *
 * The script is also the detector of dead CSS: `--dead-css` lists the class
 * selectors that no template references (classes composed at runtime are
 * reported separately, because a naive grep misses them).
 *
 * Usage:
 *   node scripts/check-tokens.mjs [--root <dir>] [--dead-css]
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));

/**
 * The custom properties PrimeNG publishes at runtime.
 *
 * The styled engine emits them per component and per scheme, so they cannot
 * be declared in `tokens.scss`. The list is the set the shell reads;
 * `src/theme/primeng-theme.spec.ts` renders the theme and asserts that every
 * name here is really emitted.
 */
export const PRIMENG_PUBLISHED_TOKENS = [
  '--p-border-radius-lg',
  '--p-border-radius-md',
  '--p-content-background',
  '--p-content-border-color',
  '--p-content-hover-background',
  '--p-primary-color',
  '--p-primary-contrast-color',
  '--p-text-color',
  '--p-text-muted-color',
];

/**
 * Raw colours that are intentional, with the reason.
 *
 * Keyed by the repository-relative path of the file; the values are the
 * literals the file is allowed to contain.
 */
export const RAW_COLOUR_ALLOWLIST = {
  'src/app/layout/layout.scss': [
    // The mask of the mobile drawer. The file belongs to the responsive
    // phase (T7), which owns the shell's stylesheet.
    'rgb(0 0 0 / 40%)',
  ],
};

/**
 * Files allowed to name both vocabularies, with the reason.
 */
export const VOCABULARY_EXEMPTIONS = {
  'src/theme/primeng-preset.ts':
    'the bridge: it feeds the app tokens into the names PrimeNG publishes',
  'src/app/layout/layout.scss':
    'the shell stylesheet belongs to the responsive phase (T7), which owns it',
};

/** The families that belong to the app's own layer. */
const APP_TOKEN_PREFIXES = [
  '--color-',
  '--text-',
  '--surface-',
  '--border-',
  '--font-',
  '--space-',
  '--radius-',
  '--shadow-',
  '--line-height-',
  '--transition-',
  '--primitive-',
  '--sidebar-',
];

const SCANNED_EXTENSIONS = ['.scss', '.html', '.ts', '.css'];

function walk(directory, out = []) {
  for (const entry of readdirSync(directory)) {
    const full = join(directory, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else out.push(full);
  }
  return out;
}

/** Removes the comments so the scanners only see code. */
export function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/.*$/gm, '$1');
}

function isScannable(file) {
  return SCANNED_EXTENSIONS.some((extension) => file.endsWith(extension));
}

/**
 * The custom properties declared by the theme layer.
 *
 * The theme layer owns the vocabulary: a token declared in a feature file
 * would be a private colour that the design system does not know about.
 */
export function declaredTokens(themeDirectory) {
  const declared = new Set();
  for (const file of walk(themeDirectory)) {
    if (!file.endsWith('.scss')) continue;
    const source = stripComments(readFileSync(file, 'utf8'));
    for (const match of source.matchAll(/(--[A-Za-z0-9_-]+)\s*:/g)) {
      declared.add(match[1]);
    }
  }
  return declared;
}

/** Every `var(--x)` reference of a source, with the line it is on. */
export function referencesOf(source) {
  const found = [];
  const lines = source.split('\n');
  lines.forEach((line, index) => {
    for (const match of line.matchAll(/var\(\s*(--[A-Za-z0-9_-]+)\s*([,)])/g)) {
      found.push({ token: match[1], hasFallback: match[2] === ',', line: index + 1 });
    }
  });
  return found;
}

/** Every raw colour of a source, with the line it is on. */
export function rawColoursOf(source) {
  const found = [];
  const lines = source.split('\n');
  lines.forEach((line, index) => {
    for (const match of line.matchAll(/#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)/g)) {
      found.push({ value: match[0], line: index + 1 });
    }
  });
  return found;
}

/**
 * Runs the three rules over the app.
 *
 * @param {string} root repository root of the frontend app.
 * @returns {{violations: Array<{rule: string, file: string, line: number, message: string}>}}
 */
export function checkTokens(root) {
  const sourceDirectory = join(root, 'src');
  const themeDirectory = join(sourceDirectory, 'theme');
  const declared = declaredTokens(themeDirectory);
  const violations = [];

  const files = walk(sourceDirectory).filter(isScannable);

  for (const file of files) {
    const relativePath = relative(root, file).split('\\').join('/');
    const source = stripComments(readFileSync(file, 'utf8'));
    const isSpec = relativePath.endsWith('.spec.ts');

    // Rule 1 — no undeclared reference.
    for (const reference of referencesOf(source)) {
      if (declared.has(reference.token)) continue;
      if (PRIMENG_PUBLISHED_TOKENS.includes(reference.token)) continue;
      if (reference.hasFallback) continue;
      violations.push({
        rule: 'undeclared-reference',
        file: relativePath,
        line: reference.line,
        message: `"${reference.token}" is referenced but never declared`,
      });
    }

    // Rule 2 — no raw colour outside the theme layer.
    if (!relativePath.startsWith('src/theme/')) {
      const allowed = RAW_COLOUR_ALLOWLIST[relativePath] ?? [];
      for (const colour of rawColoursOf(source)) {
        if (allowed.includes(colour.value)) continue;
        violations.push({
          rule: 'raw-colour',
          file: relativePath,
          line: colour.line,
          message: `"${colour.value}" is a raw colour outside the theme layer`,
        });
      }
    }

    // Rule 3 — no mixed vocabulary.
    if (!isSpec && !VOCABULARY_EXEMPTIONS[relativePath]) {
      const names = referencesOf(source).map((reference) => reference.token);
      const readsPrimeNG = names.some((name) => name.startsWith('--p-'));
      const readsApp = names.some((name) =>
        APP_TOKEN_PREFIXES.some((prefix) => name.startsWith(prefix)),
      );
      if (readsPrimeNG && readsApp) {
        violations.push({
          rule: 'mixed-vocabulary',
          file: relativePath,
          line: 1,
          message:
            'the file reads both the PrimeNG names and the app names: pick one family',
        });
      }
    }
  }

  return { violations };
}

/**
 * The class selectors no template references.
 *
 * The classes built at runtime by string concatenation are reported apart:
 * a naive grep misses them and a naive delete breaks them.
 */
export function deadClasses(root) {
  const sourceDirectory = join(root, 'src');
  const files = walk(sourceDirectory);
  const markup = files
    .filter((file) => /\.(html|ts)$/.test(file))
    .map((file) => readFileSync(file, 'utf8'))
    .join('\n');

  // The classes bound at runtime: `[class.foo]` and `[ngClass]="'foo-' + …"`.
  const bound = new Set(
    [...markup.matchAll(/\[class\.([\w-]+)\]/g)].map((match) => match[1]),
  );
  for (const match of markup.matchAll(/\[ngClass\]="'\s*([\w-]+)/g)) {
    bound.add(match[1]);
  }

  const orphans = [];
  const composed = [];

  for (const file of files.filter((entry) => entry.endsWith('.scss'))) {
    const relativePath = relative(root, file).split('\\').join('/');
    if (relativePath.startsWith('src/theme/')) continue;
    const source = stripComments(readFileSync(file, 'utf8'));

    const classes = new Set(
      (source.match(/\.[a-zA-Z][\w-]*/g) ?? []).map((name) => name.slice(1)),
    );

    // `&--modifier` expands to `.block--modifier`.
    for (const match of source.matchAll(/\.([\w-]+)[^{}]*\{[^{}]*&(--[\w-]+)/g)) {
      for (const modifier of match[2].match(/--[\w-]+/g) ?? []) {
        classes.add(match[1] + modifier);
      }
    }

    for (const name of classes) {
      if (bound.has(name)) continue;
      const escaped = name.replace(/[-]/g, '\\-');
      const pattern = new RegExp(
        '(^|[\\s"\'`(:])' + escaped + '([\\s"\'`):,;]|$)',
      );
      if (pattern.test(markup)) continue;
      orphans.push({ file: relativePath, class: name });
    }
  }

  // The classes that are composed at runtime.
  for (const file of files.filter(
    (entry) => entry.endsWith('.ts') && !entry.endsWith('.spec.ts'),
  )) {
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(/['"`]([^'"`]*'[^'"`]*)[`'"]/g)) {
      if (!match[1].includes('-')) continue;
      composed.push({
        file: relative(root, file).split('\\').join('/'),
        fragment: match[0],
      });
    }
  }

  return { orphans, composed };
}

function main(argv) {
  const rootIndex = argv.indexOf('--root');
  const root =
    rootIndex >= 0 ? resolve(argv[rootIndex + 1]) : resolve(HERE, '..');
  const wantsDeadCss = argv.includes('--dead-css');

  const { violations } = checkTokens(root);

  if (violations.length > 0) {
    console.error(`Token guard: ${violations.length} violation(s)\n`);
    for (const violation of violations) {
      console.error(
        `  [${violation.rule}] ${violation.file}:${violation.line} — ${violation.message}`,
      );
    }
    console.error('');
  } else {
    console.log('Token guard: no violations.');
  }

  if (wantsDeadCss) {
    const { orphans, composed } = deadClasses(root);
    console.log(`\nClass selectors with no reference (${orphans.length}):`);
    for (const orphan of orphans) {
      console.log(`  ${orphan.file}  .${orphan.class}`);
    }
    console.log(`\nFragments composed at runtime (${composed.length}):`);
    for (const fragment of composed) {
      console.log(`  ${fragment.file}  ${fragment.fragment}`);
    }
  }

  process.exit(violations.length > 0 ? 1 : 0);
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  main(process.argv.slice(2));
}
