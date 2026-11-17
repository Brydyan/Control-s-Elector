import { expect } from '@playwright/test';
import { test } from '../fixtures/auth.fixture';
import { loginReal } from '../fixtures/auth.fixture';
import { ActaDetallePage } from '../fixtures/pages';
import {
  anular,
  aprobar,
  registrarActa,
  tokenDe,
} from '../fixtures/actas-real';

/**
 * T-FE-10 J2: Approve Flow
 *
 * User journey:
 * 1. Navigate to an acta in ENVIADA state
 * 2. Open Aprobar modal
 * 3. Confirm approval
 * 4. Verify toast message
 * 5. Verify estado changed to APROBADA
 *
 * El acta se registra por HTTP (eso ya lo cubre J1 por UI): lo que se prueba
 * aca es la pantalla de aprobacion del coordinator contra la API real.
 */

test.describe('J2: Approve Acta Flow', () => {

  // El inbox y el detalle se prueban sobre la tabla de escritorio. En movil la
  // tabla compartida se reemplaza por tarjetas, asi que estos journeys (que
  // assertan sobre filas, menus y paginador de la tabla) no aplican.
  test.skip(
    ({ viewport }) => !!viewport && viewport.width < 768,
    'Journey de escritorio: en movil la tabla se reemplaza por tarjetas.',
  );
  const OBSERVER = { email: 'observervera@example.local', password: 'admin12345' };

  let actaId = 0;
  let tokenObserver = '';

  test.beforeEach(async ({ request }) => {
    tokenObserver = await tokenDe(request, OBSERVER.email, OBSERVER.password);
    actaId = await registrarActa(request, tokenObserver);
  });

  test.afterEach(async ({ request }) => {
    if (actaId) {
      await anular(request, actaId);
    }
  });

  test('should approve an acta in ENVIADA state', async ({ page }) => {
    await loginReal(page, 'COORDINATOR');

    const detalle = new ActaDetallePage(page);
    await detalle.goto(actaId);

    // Click Aprobar button
    await detalle.clickAprobar();

    // Verify modal opened
    await detalle.verifyModalOpen('Aprobar');

    // Click confirm
    await detalle.clickModalConfirm();

    // Verify modal closed
    await detalle.verifyModalClosed();

    // Verify the success banner (the page shows the result inline)
    await expect(page.locator('.success-banner')).toContainText(
      'APROBADA',
      { timeout: 10_000 },
    );

    // Verify status changed to APROBADA
    await expect(page.locator('app-badge').first()).toContainText('Aprobada');
  });
});
