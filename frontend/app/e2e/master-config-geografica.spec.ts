import { expect } from '@playwright/test';
import { test } from './fixtures/auth.fixture';
import { loginReal } from './fixtures/auth.fixture';
import { tokenDe } from './fixtures/actas-real';
import { USUARIOS_SEED } from './support/usuarios-seed';

/**
 * Master — Configuración Geográfica contra la API real.
 *
 * @guardian @no-intercept
 *
 * Este journey existe porque la pantalla es el único lugar donde se administran las cuatro
 * capas de la geografía electoral, y porque el árbol es la única forma de ver las relaciones
 * (provincia → cantón → parroquia → JRV). Contra la API real, falla si:
 * - la pantalla no carga el árbol completo;
 * - expandir no muestra los hijos;
 * - el alta de un cantón no deja el nodo en el árbol;
 * - un OBSERVER entra a una ruta que es de MASTER.
 */
test.describe('Master — Configuración Geográfica (API real) @guardian @no-intercept', () => {
  const master = USUARIOS_SEED.MASTER;

  test('should load the tree with the provinces', async ({ page }) => {
    await loginReal(page, 'MASTER');
    await page.goto('/master/configuracion-geografica', {
      waitUntil: 'networkidle',
      timeout: 20_000,
    });

    await expect(page.getByTestId('arbol-geografia')).toBeVisible();
    await expect(page.locator('[data-testid="nodo-PROVINCIA"]')).not.toHaveCount(0);
  });

  test('should expand a province and show its children', async ({ page }) => {
    await loginReal(page, 'MASTER');
    await page.goto('/master/configuracion-geografica', {
      waitUntil: 'networkidle',
      timeout: 20_000,
    });

    await page.locator('.p-tree-node-toggle-button').first().click();

    await expect(page.locator('[data-testid="nodo-CANTON"]')).not.toHaveCount(0);
  });

  test('should expand every level with "Expandir todo"', async ({ page }) => {
    await loginReal(page, 'MASTER');
    await page.goto('/master/configuracion-geografica', {
      waitUntil: 'networkidle',
      timeout: 20_000,
    });

    await page.getByRole('button', { name: /Expandir todo/i }).click();

    await expect(page.locator('[data-testid="nodo-CANTON"]')).not.toHaveCount(0);
    await expect(page.locator('[data-testid="nodo-PARROQUIA"]')).not.toHaveCount(0);
    await expect(page.locator('[data-testid="nodo-JRV"]')).not.toHaveCount(0);
  });

  test('should create a canton and see it in the tree', async ({
    page,
    request,
  }) => {
    const codigo = `E2E${Date.now() % 100000}`;
    const nombre = `Canton E2E ${codigo}`;

    const token = await tokenDe(request, master.email, master.password);
    const arbol = await request.get('/api/v1/master/geografia', {
      headers: { Authorization: `Bearer ${token}` },
    });
    const cuerpo = (await arbol.json()) as {
      provincias: {
        id: number;
        cantones: { id: number; nombre: string }[];
      }[];
    };
    const idProvincia = cuerpo.provincias[0].id;

    await loginReal(page, 'MASTER');
    await page.goto('/master/configuracion-geografica', {
      waitUntil: 'networkidle',
      timeout: 20_000,
    });

    // El alta es contextual: el botón vive en el nodo de la provincia.
    await page.getByTestId(`crear-canton-${idProvincia}`).click();

    await expect(page.getByTestId('input-nombre')).toBeVisible();
    await page.getByTestId('input-nombre').fill(nombre);
    await page.getByTestId('input-codigo').fill(codigo);
    await page.getByTestId('btn-save').click();

    // El árbol se recarga solo: el nodo nuevo tiene que estar.
    await expect(
      page.locator('[data-testid="nodo-CANTON"]', { hasText: nombre }),
    ).toBeVisible();

    // Limpieza: el cantón es de este test. Se busca por nombre en el listado de cantones
    // (el árbol se leyó antes de crearlo).
    const cantones = await request.get('/api/v1/configuracion/cantones', {
      headers: { Authorization: `Bearer ${token}` },
    });
    const lista = (await cantones.json()) as { id: number; nombre: string }[];
    for (const canton of lista) {
      if (canton.nombre === nombre) {
        await request.delete(`/api/v1/master/cantones/${canton.id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
      }
    }
  });

  test('should send an OBSERVER away from the master route', async ({ page }) => {
    await loginReal(page, 'OBSERVER');
    await page.goto('/master/configuracion-geografica', {
      waitUntil: 'networkidle',
      timeout: 20_000,
    });

    // El guard de rol echa a quien no es MASTER: la app lo manda a su propia pantalla
    // (el OBSERVER aterriza en "Mis JRVs"), nunca a la de MASTER.
    await expect(page).not.toHaveURL(/configuracion-geografica/);
    await expect(page.getByTestId('arbol-geografia')).toHaveCount(0);
  });
});
