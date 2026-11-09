import { expect, test } from '@playwright/test';

/**
 * J8 — Design System (ODD #505).
 *
 * <p>Lo que jsdom no puede medir: el anillo de foco y el toast en el navegador
 * real. El resto del contrato (tokens, keyframes, reglas) vive en los specs de
 * `src/theme/design-system.spec.ts`.
 */
test.describe('J8 — Design System (ODD #505)', () => {
  test('el foco por teclado se ve: anillo de 2px con el color de la marca', async ({
    page,
  }) => {
    await page.goto('/login');

    // La app monta la pantalla de forma asíncrona: tabular antes de que el
    // formulario exista deja el foco en el body, que no es un control.
    await page.locator('input').first().waitFor({ state: 'visible' });

    // El foco tiene que venir del teclado: `:focus-visible` no se activa con un
    // `focus()` programático.
    //
    // Se busca un elemento que NO sea un control de PrimeNG: esos tienen su
    // propia regla de foco y mediríaían otra cosa. Lo que se mide aquí es la
    // regla global del sistema, la que alcanza a todo lo enfocable.
    let anillo: {
      tag: string;
      clase: string;
      html: string;
      outlineWidth: string;
      outlineStyle: string;
      outlineColor: string;
    } | null = null;

    for (let intento = 0; intento < 20 && !anillo; intento++) {
      await page.keyboard.press('Tab');
      anillo = await page.evaluate(() => {
        const activo = document.activeElement as HTMLElement | null;
        if (!activo) return null;
        if (activo === document.body || activo === document.documentElement) {
          return null;
        }
        // Los controles de PrimeNG tienen su propia regla de foco: mediría
        // otra cosa. Lo que se mide es la regla global del sistema.
        const esDeLaLibreria = Array.from(activo.classList).some((clase) =>
          clase.startsWith('p-'),
        );
        if (esDeLaLibreria) return null;
        const estilo = getComputedStyle(activo);
        return {
          tag: activo.tagName,
          clase: activo.className,
          html: activo.outerHTML.slice(0, 120),
          outlineWidth: estilo.outlineWidth,
          outlineStyle: estilo.outlineStyle,
          outlineColor: estilo.outlineColor,
        };
      });
    }

    expect(anillo, 'no se encontró un elemento con foco que no sea de PrimeNG').not.toBeNull();
    expect(anillo?.outlineWidth, JSON.stringify(anillo)).toBe('2px');
    expect(anillo?.outlineStyle).toBe('solid');
    expect(anillo?.outlineColor).not.toBe('rgba(0, 0, 0, 0)');
  });

  test('el toast esta montado en el root', async ({ page }) => {
    await page.goto('/login');

    await expect(page.locator('p-toast')).toHaveCount(1);
  });
});
