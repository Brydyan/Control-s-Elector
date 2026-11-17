import { expect } from '@playwright/test';
import { test } from '../fixtures/auth.fixture';
import { loginReal } from '../fixtures/auth.fixture';
import {
  anular,
  aprobar,
  registrarActa,
  tokenDe,
} from '../fixtures/actas-real';

/**
 * T-FE-10 J4: Anular Acta (MASTER-only)
 *
 * User journey:
 * 1. An APROBADA acta shows up in the coordinator's inbox
 * 2. As COORDINATOR: the "Anular" action is not offered
 * 3. As MASTER: the action is offered, and the acta can be annulled
 * 4. The acta ends up ANULADA
 *
 * La anulacion es irreversible: el estado ANULADA es terminal en el backend.
 */

test.describe('J4: Anular Acta (MASTER-only)', () => {

  // El inbox y el detalle se prueban sobre la tabla de escritorio. En movil la
  // tabla compartida se reemplaza por tarjetas, asi que estos journeys (que
  // assertan sobre filas, menus y paginador de la tabla) no aplican.
  test.skip(
    ({ viewport }) => !!viewport && viewport.width < 768,
    'Journey de escritorio: en movil la tabla se reemplaza por tarjetas.',
  );
  const OBSERVER = { email: 'observervera@example.local', password: 'admin12345' };

  let actaId = 0;

  test.beforeEach(async ({ request }) => {
    const tokenObserver = await tokenDe(
      request,
      OBSERVER.email,
      OBSERVER.password,
    );
    actaId = await registrarActa(request, tokenObserver);
    // La anulacion solo aplica a las actas APROBADAS.
    await aprobar(request, actaId);
  });

  test.afterEach(async ({ request }) => {
    if (actaId) {
      await anular(request, actaId);
    }
  });

  test('should disable Anular for COORDINATOR, enable for MASTER', async ({
    page,
  }) => {
    // --- COORDINATOR: la accion no esta ---
    await loginReal(page, 'COORDINATOR');
    await page.goto('/coordinator/actas');

    const fila = page.locator('tbody tr').first();
    await expect(fila).toBeVisible({ timeout: 15_000 });

    await fila.locator('[aria-label="Acciones de acta"]').first().click();
    const menu = page.locator('[role="menuitem"]');
    await expect(menu.first()).toBeVisible({ timeout: 5_000 });

    // El menu ofrece Revisar, Aprobar, Disputar... pero no Anular: la
    // accion es exclusiva del MASTER.
    await expect(menu.filter({ hasText: 'Anular' })).toHaveCount(0);

    // --- MASTER: la accion esta y se puede ejecutar ---
    await loginReal(page, 'MASTER');
    await page.goto('/coordinator/actas');

    const filaMaster = page.locator('tbody tr').first();
    await expect(filaMaster).toBeVisible({ timeout: 15_000 });

    await filaMaster
      .locator('[aria-label="Acciones de acta"]')
      .first()
      .click();

    const itemAnular = page.locator('[role="menuitem"]', { hasText: 'Anular' });
    await expect(itemAnular).toBeVisible({ timeout: 5_000 });
    await itemAnular.click();

    // El motivo es obligatorio.
    await page.getByTestId('anular-motivo').fill('Acta fraudulenta');
    await page
      .locator('[role="dialog"] button')
      .filter({ hasText: /Anular/ })
      .last()
      .click();

    // El estado final es ANULADA.
    await expect(page.getByTestId('anular-motivo')).toHaveCount(0, {
      timeout: 15_000,
    });

    const detalle = await page.request.get(
      `/api/v1/coordinator/actas/${actaId}`,
      {
        headers: {
          Authorization: `Bearer ${await tokenDe(
            page.request,
            'admin@control-elector.local',
            'admin12345',
          )}`,
        },
      },
    );
    const acta = (await detalle.json()) as { estado: string };
    expect(acta.estado).toBe('ANULADA');
  });
});
