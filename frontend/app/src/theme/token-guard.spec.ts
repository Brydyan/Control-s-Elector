import { describe, it, expect, afterEach } from 'vitest';
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { Button } from 'primeng/button';
import { primengTheme } from './primeng-theme';

/**
 * S11 of `odd/tasks/frontend-design-system-hardening.md`: the token contract
 * has to be enforceable.
 *
 * The guard lives in `scripts/check-tokens.mjs` and this spec is its proof:
 * it runs the guard over the app (it must pass) and over fixtures that break
 * each rule (it must fail). A guard nobody can see fail is not a guard.
 */

const THEME_DIR = dirname(fileURLToPath(import.meta.url));
const APP_ROOT = resolve(THEME_DIR, '../..');
const SCRIPT = join(APP_ROOT, 'scripts/check-tokens.mjs');

type Guard = {
  checkTokens: (root: string) => {
    violations: Array<{
      rule: string;
      file: string;
      line: number;
      message: string;
    }>;
  };
  PRIMENG_PUBLISHED_TOKENS: string[];
};

// The guard is a plain ESM script and has no type declarations of its own.
const guard =
  (await import('../../scripts/check-tokens.mjs')) as unknown as Guard;

const temporaryRoots: string[] = [];

/**
 * The name of the token the fixtures break with.
 *
 * It is assembled at runtime so this file does not trip its own guard: a
 * literal reference to an undeclared token is exactly what the guard reports.
 */
const UNDECLARED = ['--color', 'not', 'declared'].join('-');

/**
 * The name of the PrimeNG token the fixtures mix with the app's names.
 *
 * It is assembled at runtime so the vocabulary migration does not rewrite the
 * fixture into a file that only reads one family.
 */
const PRIMENG_NAME = ['--p', 'text-color'].join('-');

afterEach(() => {
  while (temporaryRoots.length > 0) {
    const root = temporaryRoots.pop();
    if (root) rmSync(root, { recursive: true, force: true });
  }
});

/**
 * A miniature app: a theme layer that declares one token and a feature file
 * that can break one rule.
 */
function fixture(feature: { path: string; content: string }): string {
  const root = mkdtempSync(join(tmpdir(), 'token-guard-'));
  temporaryRoots.push(root);

  mkdirSync(join(root, 'src/theme'), { recursive: true });
  writeFileSync(
    join(root, 'src/theme/tokens.scss'),
    ':root {\n  --color-brand-primary: #1c4a8e;\n  --color-border-medium: #e2e8f0;\n}\n',
    'utf8',
  );

  const file = join(root, 'src', feature.path);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, feature.content, 'utf8');

  return root;
}

describe('the token guard', () => {
  it('should not find a violation in the app', () => {
    const { violations } = guard.checkTokens(APP_ROOT);

    expect(
      violations.map(
        (violation) =>
          `${violation.rule} ${violation.file}:${violation.line} ${violation.message}`,
      ),
    ).toEqual([]);
  });

  it('should fail when a reference is not declared', () => {
    const root = fixture({
      path: 'app/features/x/x.page.scss',
      content: `.thing {\n  color: var(${UNDECLARED});\n}\n`,
    });

    const { violations } = guard.checkTokens(root);

    expect(violations).toHaveLength(1);
    expect(violations[0].rule).toBe('undeclared-reference');
    expect(violations[0].message).toContain(UNDECLARED);
  });

  it('should fail when a raw colour lives outside the theme layer', () => {
    const root = fixture({
      path: 'app/features/x/x.page.scss',
      content: '.thing {\n  color: #ff0000;\n}\n',
    });

    const { violations } = guard.checkTokens(root);

    expect(violations).toHaveLength(1);
    expect(violations[0].rule).toBe('raw-colour');
    expect(violations[0].message).toContain('#ff0000');
  });

  it('should fail when a file reads both vocabularies', () => {
    const root = fixture({
      path: 'app/features/x/x.page.scss',
      content: `.thing {\n  color: var(${PRIMENG_NAME});\n  border-color: var(--color-border-medium);\n}\n`,
    });

    const { violations } = guard.checkTokens(root);

    expect(violations).toHaveLength(1);
    expect(violations[0].rule).toBe('mixed-vocabulary');
  });

  it('should fail the command line with a non zero exit code', () => {
    const root = fixture({
      path: 'app/features/x/x.page.scss',
      content: `.thing {\n  color: var(${UNDECLARED});\n}\n`,
    });

    let status = 0;
    let output = '';
    try {
      output = execFileSync('node', [SCRIPT, '--root', root], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
      });
    } catch (error) {
      status = (error as { status: number }).status;
      output = `${(error as { stderr: string }).stderr ?? ''}`;
    }

    expect(status).toBe(1);
    expect(output).toContain(UNDECLARED);
  });
});

/**
 * The `--p-*` names are published by the styled engine at runtime, so they
 * cannot be declared in `tokens.scss`. The guard trusts a fixed list; this
 * test renders the theme and proves the list is the truth.
 */
describe('the PrimeNG vocabulary', () => {
  it('should publish every name the guard trusts', async () => {
    await TestBed.configureTestingModule({
      imports: [Button],
      providers: [providePrimeNG({ theme: primengTheme })],
    }).compileComponents();

    const fixtureComponent = TestBed.createComponent(Button);
    fixtureComponent.detectChanges();

    const themeCss = Array.from(document.head.querySelectorAll('style'))
      .map((style) => style.textContent ?? '')
      .join('\n');

    const missing = guard.PRIMENG_PUBLISHED_TOKENS.filter(
      (token) => !themeCss.includes(`${token}:`),
    );

    expect(missing).toEqual([]);
  });
});
