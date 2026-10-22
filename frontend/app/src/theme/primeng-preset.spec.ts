import { describe, it, expect } from 'vitest';
import Aura from '@primeuix/themes/aura';
import { electoralPreset, BRAND_BLUE, BRAND_RADIUS } from './primeng-preset';
import { primengTheme, DARK_MODE_CLASS } from './primeng-theme';

/**
 * S6: the institutional palette must survive the migration to PrimeNG.
 * These assertions pin the literal values declared in `src/theme/tokens.scss`.
 */
describe('electoralPreset', () => {
  it('should map the primary ramp to the institutional blue scale', () => {
    const primary = electoralPreset.semantic.primary as Record<string, string>;

    expect(primary['50']).toBe('#eff6ff');
    expect(primary['100']).toBe('#dbeafe');
    expect(primary['200']).toBe('#bfdbfe');
    expect(primary['500']).toBe('#2563eb');
    expect(primary['600']).toBe('#1d4ed8');
    expect(primary['700']).toBe('#1e40af');
    expect(primary['800']).toBe('#1e3a8a');
    expect(primary['950']).toBe('#0f172a');
  });

  it('should not ship the Aura default emerald primary', () => {
    const primary = electoralPreset.semantic.primary as Record<string, string>;

    expect(primary['500']).not.toBe(
      (Aura.semantic.primary as Record<string, string>)['500'],
    );
  });

  it('should keep the derived colour scheme tokens inherited from Aura', () => {
    const colorScheme = electoralPreset.semantic.colorScheme as {
      light: { primary: Record<string, string>; text: Record<string, string> };
      dark: { primary: Record<string, string>; text: Record<string, string> };
    };

    // definePreset merges: the derived light/dark primary tokens must survive
    // the override of the numeric shades.
    expect(colorScheme.light.primary['hoverColor']).toBe(
      'var(--color-brand-primary-strong)',
    );
    expect(colorScheme.light.primary['activeColor']).toBe(
      'var(--color-brand-primary-active)',
    );
    // The tokens the bridge does not own keep Aura's value.
    expect(colorScheme.light.text['hoverColor']).toBe('{surface.800}');
    expect(colorScheme.dark.text['hoverColor']).toBe('{surface.0}');
  });

  it('should feed the app tokens into the names PrimeNG publishes', () => {
    const colorScheme = electoralPreset.semantic.colorScheme as {
      light: Record<string, Record<string, string>>;
      dark: Record<string, Record<string, string>>;
    };

    // The bridge: the app's layer owns the vocabulary.
    expect(colorScheme.light['text']['color']).toBe(
      'var(--color-text-on-surface)',
    );
    expect(colorScheme.light['text']['mutedColor']).toBe(
      'var(--color-text-muted-on-surface)',
    );
    expect(colorScheme.light['content']['background']).toBe(
      'var(--color-surface-card)',
    );
    expect(colorScheme.light['content']['hoverBackground']).toBe(
      'var(--color-surface-raised)',
    );
    expect(colorScheme.light['content']['borderColor']).toBe(
      'var(--color-border-on-surface)',
    );
    expect(colorScheme.light['primary']['color']).toBe(
      'var(--color-brand-primary)',
    );
    expect(colorScheme.light['primary']['contrastColor']).toBe(
      'var(--color-text-on-brand)',
    );

    // The dark scheme declares the same names.
    expect(colorScheme.dark['text']['color']).toBe(
      'var(--color-text-on-surface)',
    );
    expect(colorScheme.dark['primary']['color']).toBe(
      'var(--color-brand-primary)',
    );
  });

  it('should override the blue primitive with the same institutional ramp', () => {
    const primitive = electoralPreset.primitive as Record<string, unknown>;

    expect(primitive['blue']).toEqual(BRAND_BLUE);
  });

  it('should map the institutional radius scale onto PrimeUI stops', () => {
    const primitive = electoralPreset.primitive as Record<string, unknown>;

    expect(primitive['borderRadius']).toEqual(BRAND_RADIUS);
    expect(BRAND_RADIUS.sm).toBe('6px');
    expect(BRAND_RADIUS.md).toBe('8px');
    expect(BRAND_RADIUS.lg).toBe('12px');
    expect(BRAND_RADIUS.xl).toBe('16px');
  });

  it('should keep the institutional slate ramp in the dark scheme', () => {
    const colorScheme = electoralPreset.semantic.colorScheme as {
      dark: { surface: Record<string, string> };
    };

    expect(colorScheme.dark.surface['900']).toBe('{slate.900}');
    expect(colorScheme.dark.surface['950']).toBe('{slate.950}');
    expect(colorScheme.dark.surface['800']).toBe('{slate.800}');
  });

  it('should inherit component tokens from Aura', () => {
    const components = electoralPreset.components as Record<string, unknown>;

    expect(components['card']).toBeDefined();
    expect(components['datatable']).toBeDefined();
  });
});

describe('primengTheme', () => {
  it('should use the institutional preset', () => {
    expect(primengTheme.preset).toBe(electoralPreset);
  });

  it('should activate the dark scheme through an explicit class', () => {
    expect(primengTheme.options.darkModeSelector).toBe(`.${DARK_MODE_CLASS}`);
    expect(DARK_MODE_CLASS).toBe('app-dark');
  });
});
