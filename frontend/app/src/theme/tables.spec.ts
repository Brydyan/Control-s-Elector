import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseNestedScssRules, parseThemeTokens } from './tokens.parser';

/**
 * The contract of the table standard.
 *
 * `.app-table` is a global stylesheet, so nothing forces a page to use it:
 * a `p-table` without the class renders with whatever PrimeNG emits, and
 * PrimeNG 21 does not put a single class on the `<th>` that the template
 * writes. The table then shows a header with no padding and no band, next to
 * another table that has both. That is exactly the inconsistency this
 * standard exists to remove, and it is invisible until somebody adds a new
 * table without the class.
 *
 * So the standard is checked here: every table of the app carries the class,
 * and the class reaches the four pieces of a table —the header, the row, the
 * cell and the paginator.
 */

const THEME_DIR = dirname(fileURLToPath(import.meta.url));
const SOURCE_DIR = resolve(THEME_DIR, '..');
const APP_DIR = join(SOURCE_DIR, 'app');

/** Every template of the app. */
function templates(directory: string): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(directory)) {
    const full = join(directory, entry);
    if (statSync(full).isDirectory()) found.push(...templates(full));
    else if (full.endsWith('.html')) found.push(full);
  }
  return found;
}

/** The `p-table` tags of a template, with their attributes. */
function tableTags(source: string): string[] {
  return [...source.matchAll(/<p-table\b([\s\S]*?)>/g)].map(
    (match) => match[1],
  );
}

/** The `styleClass` of a `p-table` tag, if it declares one. */
function styleClassOf(attributes: string): string {
  return attributes.match(/styleClass\s*=\s*"([^"]*)"/)?.[1] ?? '';
}

const tables = templates(APP_DIR).flatMap((file) =>
  tableTags(readFileSync(file, 'utf8')).map((attributes) => ({
    file: file.replace(`${SOURCE_DIR}/`, ''),
    styleClass: styleClassOf(attributes),
  })),
);

describe('the table standard', () => {
  it('should find the tables of the app', () => {
    expect(tables.length).toBeGreaterThan(0);
  });

  it.each(tables)(
    'should carry the standard class in $file',
    ({ styleClass }) => {
      expect(styleClass.split(/\s+/)).toContain('app-table');
    },
  );

  it('should reach the four pieces of a table', () => {
    const source = readFileSync(join(THEME_DIR, 'tables.scss'), 'utf8');
    const selectors = parseNestedScssRules(source).map((rule) =>
      rule.selector.replace(/\s+/g, ' ').trim(),
    );

    expect(selectors).toContain('.app-table .p-datatable-thead > tr > th');
    expect(selectors).toContain('.app-table .p-datatable-tbody > tr');
    expect(selectors).toContain('.app-table .p-datatable-tbody > tr > td');
    expect(selectors).toContain('.app-table .p-paginator');
  });

  it('should declare the tokens of the table in both schemes', () => {
    const tokens = parseThemeTokens(
      readFileSync(join(THEME_DIR, 'tokens.scss'), 'utf8'),
    );

    const tableTokens = [
      '--color-table-surface',
      '--color-table-header-bg',
      '--color-table-header-text',
      '--color-table-header-border',
      '--color-table-cell-text',
      '--color-table-row-border',
      '--color-table-row-hover-bg',
      '--color-table-caption-text',
    ];

    for (const token of tableTokens) {
      expect(Object.keys(tokens.light), token).toContain(token);
      expect(Object.keys(tokens.dark), token).toContain(token);
    }
  });
});
