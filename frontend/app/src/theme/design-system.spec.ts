import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/**
 * El contrato del sistema de diseño (ODD 505, B/C/D).
 *
 * <p>jsdom no calcula pseudo-clases ni estilos de hoja de estilos: el anillo de
 * foco se mide en el navegador (spec E2E). Lo que sí se puede comprobar aquí es
 * el **contrato** —que los tokens tengan los valores que el sistema promete y que
 * las reglas que los usan existan— que es justo lo que rompe una mutación.
 */
const THEME_DIR = dirname(fileURLToPath(import.meta.url));
const leer = (nombre: string) =>
  readFileSync(resolve(THEME_DIR, nombre), 'utf8');

const tokens = leer('tokens.scss');
const focusRing = leer('focus-ring.scss');
const animations = leer('animations.scss');
const tailwind = leer('tailwind.css');

describe('la escala de espaciado (ODD 505, B)', () => {
  it('declara la escala completa de 4px', () => {
    const escala: Record<string, string> = {
      '1': '4px',
      '2': '8px',
      '3': '12px',
      '4': '16px',
      '5': '20px',
      '6': '24px',
      '7': '28px',
      '8': '32px',
      '9': '36px',
      '10': '40px',
      '12': '48px',
    };

    for (const [paso, valor] of Object.entries(escala)) {
      expect(tokens).toContain(`--space-${paso}: ${valor};`);
    }
  });

  it('las utilidades de espaciado las da Tailwind, con la misma escala', () => {
    // Tailwind v4 ya emite `p-*`, `gap-*` y `m-*` con multiples de 4px: duplicar
    // esas clases en `utilities.scss` seria tener dos fuentes de verdad para el
    // mismo valor, y Tailwind gana por orden de carga.
    expect(tailwind).toContain("@import 'tailwindcss'");
  });
});

describe('el anillo de foco (ODD 505, C)', () => {
  it('el token del anillo declara 2px de grosor y 2px de separacion', () => {
    expect(tokens).toContain('--focus-ring-width: 2px;');
    expect(tokens).toContain('--focus-ring-offset: 2px;');
    expect(tokens).toContain('--focus-ring-color: var(--color-brand-primary);');
  });

  it('la regla :focus-visible usa los tokens del anillo', () => {
    // El bloque global, no el archivo entero: los controles de PrimeNG tienen su
    // propia regla y una mutación que vacie la global tiene que notarse.
    const bloque = focusRing.match(/:focus-visible\s*\{([^}]*)\}/)?.[1] ?? '';

    expect(bloque).toContain('var(--focus-ring-width)');
    expect(bloque).toContain('var(--focus-ring-color)');
    expect(bloque).toContain('var(--focus-ring-offset)');
  });

  it('los controles de PrimeNG no dejan su sombra de foco encima', () => {
    expect(focusRing).toContain('box-shadow: none');
  });
});

describe('las transiciones (ODD 505, D)', () => {
  it('las duraciones estan en la banda de 100 a 300ms', () => {
    expect(tokens).toContain('--duration-fast: 100ms;');
    expect(tokens).toContain('--duration-normal: 200ms;');
    expect(tokens).toContain('--duration-slow: 300ms;');
  });

  it('las transiciones se declaran por propiedad, no con `all`', () => {
    // `transition: all` se lleva por delante cambios que nadie pidio.
    expect(tokens).not.toContain('--transition-fast: all');
    expect(tokens).not.toContain('--transition-normal: all');
    expect(tokens).not.toContain('--transition-slow: all');
  });

  it('los keyframes del sistema estan definidos', () => {
    for (const nombre of [
      'fadeIn',
      'slideUp',
      'slideDown',
      'spinIn',
      'shimmer',
    ]) {
      expect(animations).toContain(`@keyframes ${nombre}`);
    }
  });

  it('respeta prefers-reduced-motion', () => {
    expect(animations).toContain('prefers-reduced-motion: reduce');
    expect(animations).toContain('transition-duration: 0.01ms !important');
  });
});
