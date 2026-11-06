import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { Button } from 'primeng/button';
import { primengTheme } from './primeng-theme';
import { parseThemeTokens, ThemeResolver } from './tokens.parser';
import { LIGHT_BRIDGE, DARK_BRIDGE } from './primeng-preset';

/**
 * S11 of `odd/tasks/frontend-design-system-hardening.md`: the app's layer owns
 * the vocabulary and the component library is fed from it.
 *
 * The bridge moves names around, so the contract it has to keep is the one
 * that matters: **every name the bridge feeds has to resolve to whatever
 * value the app's layer declares for it, in both schemes**. The "before"
 * column below is the value the running app paints *after* the visual
 * rebalance (`odd/tasks/frontend-visual-rebalance.md`): the bridge itself
 * does not move hex around — it only points at names — so when the app's
 * `--color-brand-primary` changes hue from `#2563eb` to `#1c4a8e`, the
 * column follows. The contract is name-only: if a rebalance ever breaks
 * this table, the bridge is the thing that hid the change.
 */

const THEME_DIR = dirname(fileURLToPath(import.meta.url));

const { light, dark } = parseThemeTokens(
  readFileSync(join(THEME_DIR, 'tokens.scss'), 'utf8'),
);
const resolver = new ThemeResolver(light, dark);

interface BridgeEntry {
  /** The name PrimeNG publishes. */
  prime: string;
  /** The app token the light scheme feeds it with. */
  light: string;
  /** The app token the dark scheme feeds it with. */
  dark: string;
  /** The value the app painted before the bridge. */
  before: { light: string; dark: string };
}

const BRIDGE: BridgeEntry[] = [
  {
    prime: '--p-text-color',
    light: LIGHT_BRIDGE.text.color,
    dark: DARK_BRIDGE.text.color,
    before: { light: '#334155', dark: '#ffffff' },
  },
  {
    prime: '--p-text-muted-color',
    light: LIGHT_BRIDGE.text.mutedColor,
    dark: DARK_BRIDGE.text.mutedColor,
    before: { light: '#64748b', dark: '#94a3b8' },
  },
  {
    prime: '--p-content-background',
    light: LIGHT_BRIDGE.content.background,
    dark: DARK_BRIDGE.content.background,
    before: { light: '#ffffff', dark: '#0f172a' },
  },
  {
    prime: '--p-content-hover-background',
    light: LIGHT_BRIDGE.content.hoverBackground,
    dark: DARK_BRIDGE.content.hoverBackground,
    before: { light: '#f1f5f9', dark: '#1e293b' },
  },
  {
    prime: '--p-content-border-color',
    light: LIGHT_BRIDGE.content.borderColor,
    dark: DARK_BRIDGE.content.borderColor,
    before: { light: '#e2e8f0', dark: '#334155' },
  },
  {
    prime: '--p-primary-color',
    light: LIGHT_BRIDGE.primary.color,
    dark: DARK_BRIDGE.primary.color,
    before: { light: '#1c4a8e', dark: '#4d75a8' },
  },
  {
    prime: '--p-primary-contrast-color',
    light: LIGHT_BRIDGE.primary.contrastColor,
    dark: DARK_BRIDGE.primary.contrastColor,
    before: { light: '#ffffff', dark: '#0f172a' },
  },
];

/** The token a `var(--x)` string points at. */
function tokenOf(reference: string): string {
  const match = reference.match(/var\(\s*(--[A-Za-z0-9_-]+)\s*\)/);
  if (!match) throw new Error(`"${reference}" does not name a token`);
  return match[1];
}

describe('the colour bridge', () => {
  it('should feed every name the app reads from the app own layer', () => {
    for (const entry of BRIDGE) {
      // The bridge is the theme layer: it may name a semantic token of the app
      // or one of its primitives, but nothing outside the layer.
      for (const reference of [entry.light, entry.dark]) {
        const token = tokenOf(reference);
        expect(
          Object.keys(light).includes(token) ||
            Object.keys(dark).includes(token),
          `${token} is not declared by the app layer`,
        ).toBe(true);
      }
    }
  });

  it.each(BRIDGE)(
    'should keep $prime painting the same colour in both schemes',
    (entry) => {
      expect(resolver.resolve(tokenOf(entry.light), 'light')).toBe(
        entry.before.light,
      );
      expect(resolver.resolve(tokenOf(entry.dark), 'dark')).toBe(
        entry.before.dark,
      );
    },
  );

  it('should publish the bridge in the theme the engine emits', async () => {
    await TestBed.configureTestingModule({
      imports: [Button],
      providers: [providePrimeNG({ theme: primengTheme })],
    }).compileComponents();

    const fixture = TestBed.createComponent(Button);
    fixture.detectChanges();

    const themeCss = Array.from(document.head.querySelectorAll('style'))
      .map((style) => style.textContent ?? '')
      .join('\n');

    for (const entry of BRIDGE) {
      const pattern = new RegExp(
        `${entry.prime}\\s*:\\s*var\\(\\s*${tokenOf(entry.light)}\\s*\\)`,
      );
      expect(themeCss, `${entry.prime} is not fed by the app layer`).toMatch(
        pattern,
      );
    }
  });
});
