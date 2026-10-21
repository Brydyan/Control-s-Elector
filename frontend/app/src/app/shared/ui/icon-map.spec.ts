import { describe, it, expect } from 'vitest';
import { ICON_MAP, FALLBACK_ICON, toPrimeIcon } from './icon-map';

/**
 * S11 of `odd/tasks/frontend-design-system-hardening.md`: there were two icon
 * tables — sixty Ionic names in this file and a switch of eleven backend keys
 * inside the sidebar — and they resolved to the same fallback glyph.
 *
 * There is one table now, keyed by what the API sends.
 */
describe('the icon table', () => {
  it('should resolve the keys of the backend catalog', () => {
    expect(toPrimeIcon('home')).toBe('pi pi-home');
    expect(toPrimeIcon('document')).toBe('pi pi-file');
    expect(toPrimeIcon('add-circle')).toBe('pi pi-plus-circle');
    expect(toPrimeIcon('checkmark')).toBe('pi pi-check-circle');
    expect(toPrimeIcon('image')).toBe('pi pi-image');
    expect(toPrimeIcon('cloud-upload')).toBe('pi pi-cloud-upload');
    expect(toPrimeIcon('settings')).toBe('pi pi-cog');
    expect(toPrimeIcon('people')).toBe('pi pi-users');
    expect(toPrimeIcon('pulse')).toBe('pi pi-chart-line');
  });

  it('should resolve the Ionic names the routes still write', () => {
    // `src/app/app.routes.ts` names the icons of the placeholder routes with
    // the names of the Ionic migration; they resolve to the same glyph.
    expect(toPrimeIcon('document-text-outline')).toBe('pi pi-file');
    expect(toPrimeIcon('people-outline')).toBe('pi pi-users');
    expect(toPrimeIcon('pulse-outline')).toBe('pi pi-chart-line');
  });

  it('should fall back to the document glyph', () => {
    expect(toPrimeIcon('a-key-nobody-sends')).toBe(FALLBACK_ICON);
    expect(toPrimeIcon(undefined)).toBe(FALLBACK_ICON);
    expect(toPrimeIcon(null)).toBe(FALLBACK_ICON);
    expect(toPrimeIcon('')).toBe(FALLBACK_ICON);
  });

  it('should name every entry with a PrimeIcons class', () => {
    for (const [key, icon] of Object.entries(ICON_MAP)) {
      expect(icon, key).toMatch(/^pi pi-[a-z-]+$/);
    }
  });
});
