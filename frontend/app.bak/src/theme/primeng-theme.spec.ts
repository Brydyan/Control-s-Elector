import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { primengTheme } from './primeng-theme';
import { isSemanticToken, parseThemeTokens } from './tokens.parser';

/**
 * The stylesheets of the pages rely on the PrimeNG theme variables. The styled
 * engine emits them on demand, per component, so a variable that no rendered
 * component references is simply not defined and the rule that uses it falls
 * back to nothing.
 *
 * This test renders the components the shell is made of and asserts that every
 * variable the own stylesheets depend on is actually emitted.
 */
const REQUIRED_TOKENS = [
  '--p-primary-color',
  '--p-primary-contrast-color',
  '--p-content-background',
  '--p-content-border-color',
  '--p-content-hover-background',
  '--p-text-color',
  '--p-text-muted-color',
  '--p-border-radius-md',
  '--p-border-radius-lg',
];

describe('PrimeNG theme variables', () => {
  let themeCss: string;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Button, Card],
      providers: [providePrimeNG({ theme: primengTheme })],
    }).compileComponents();

    const fixture = TestBed.createComponent(Button);
    fixture.detectChanges();

    themeCss = Array.from(document.head.querySelectorAll('style'))
      .map((style) => style.textContent ?? '')
      .join('\n');
  });

  it('should emit the variables the own stylesheets depend on', () => {
    const missing = REQUIRED_TOKENS.filter(
      (token) => !themeCss.includes(`${token}:`),
    );

    expect(missing).toEqual([]);
  });
});

/**
 * S8 of `odd/tasks/frontend-design-system-hardening.md`.
 *
 * A `var(--x)` that nothing declares does not fail loudly: the declaration is
 * dropped at computed-value time, so a shorthand (`border-left: 4px solid
 * var(--missing)`) loses the whole declaration and the rule paints nothing.
 * That is how the checksum banner lost its container and the state border
 * rendered as `0px solid`.
 */
const SOURCE_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SCANNED_EXTENSIONS = ['.scss', '.html', '.ts'];

function collectSourceFiles(directory: string): string[] {
  const files: string[] = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const full = join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectSourceFiles(full));
    } else if (
      SCANNED_EXTENSIONS.some((extension) => entry.name.endsWith(extension)) &&
      !entry.name.endsWith('.spec.ts')
    ) {
      files.push(full);
    }
  }
  return files;
}

function collectTokenReferences(files: string[]): Map<string, string[]> {
  const references = new Map<string, string[]>();
  const pattern = /var\(\s*(--[A-Za-z0-9_-]+)/g;

  for (const file of files) {
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(pattern)) {
      const token = match[1];
      const places = references.get(token) ?? [];
      places.push(relative(SOURCE_DIR, file));
      references.set(token, places);
    }
  }

  return references;
}

describe('design token contract', () => {
  const tokens = parseThemeTokens(
    readFileSync(resolve(SOURCE_DIR, 'theme/tokens.scss'), 'utf8'),
  );
  const defined = new Set([
    ...Object.keys(tokens.light),
    ...Object.keys(tokens.dark),
  ]);

  it('should not reference a custom property that is never declared', () => {
    const references = collectTokenReferences(collectSourceFiles(SOURCE_DIR));

    // The `--p-*` namespace belongs to PrimeNG: the styled engine emits it
    // per component and the test above guards the ones the app depends on.
    const undefinedTokens = [...references.entries()]
      .filter(([token]) => !token.startsWith('--p-'))
      .filter(([token]) => !defined.has(token))
      .map(([token, places]) => `${token} (${places.join(', ')})`);

    expect(undefinedTokens).toEqual([]);
  });

  it('should declare the same semantic tokens in both schemes', () => {
    const lightSemantic = Object.keys(tokens.light)
      .filter(isSemanticToken)
      .sort();
    const darkSemantic = Object.keys(tokens.dark)
      .filter(isSemanticToken)
      .sort();

    expect(darkSemantic).toEqual(lightSemantic);
  });
});
