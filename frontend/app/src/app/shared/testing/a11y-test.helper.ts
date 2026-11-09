import { expect } from 'vitest';
import { axe } from 'vitest-axe';

/**
 * Comprueba que un elemento no tiene violaciones de accesibilidad (ODD 505, E).
 *
 * <p>El aserto es explícito y no usa el matcher de `vitest-axe` a propósito: cuando
 * algo falla, el mensaje dice **qué regla** se violó y cuántos nodos la violan.
 * Un `expect(true).toBe(true)` no dice nada, y un matcher que solo dice "expected
 * no violations" obliga a ir a buscar el detalle.
 *
 * <p>El análisis corre en jsdom: axe evalúa lo que el DOM declara (roles, nombres,
 * relaciones entre elementos) y deja fuera lo que necesita layout real —el
 * contraste, por ejemplo, que se mide en el navegador.
 *
 * @param element el elemento a analizar.
 */
export async function expectA11yClean(element: HTMLElement): Promise<void> {
  const results = await axe(element);
  const violations = results.violations.map(
    (violation) =>
      `${violation.id}: ${violation.help} (${violation.nodes.length} nodo/s)`,
  );

  expect(violations).toEqual([]);
}

/**
 * Las violaciones de un elemento, para el test que comprueba que el analizador
 * **sí** ve lo que está mal.
 *
 * <p>Sin este contrapeso, un "0 violaciones" siempre verde no prueba nada: basta
 * con que el analizador no mire. Este helper es el que permite comprobar que un
 * botón sin nombre accesible **cae**.
 *
 * @param element el elemento a analizar.
 * @returns los identificadores de las reglas violadas.
 */
export async function a11yViolations(element: HTMLElement): Promise<string[]> {
  const results = await axe(element);
  return results.violations.map((violation) => violation.id);
}
