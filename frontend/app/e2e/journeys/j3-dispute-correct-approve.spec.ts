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
 * T-FE-10 J3: Dispute → Correct → Approve
 *
 * User journey:
 * 1. An ENVIADA acta is disputed by the coordinator (with a reason)
 * 2. The observer corrects the votes of the disputed acta
 * 3. The coordinator approves it
 *
 * El ciclo completo de la maquina de estados, contra la API real:
 * ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA.
 */

test.describe('J3: Dispute → Correct → Approve Flow', () => {

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
  });

  test.afterEach(async ({ request }) => {
    if (actaId) {
      await anular(request, actaId);
    }
  });

  test('should complete dispute → correct → approve workflow', async ({
    page,
    request,
  }) => {
    // --- 1. El coordinator disputa el acta ---
    await loginReal(page, 'COORDINATOR');
    const detalle = new ActaDetallePage(page);
    await detalle.goto(actaId);

    await detalle.clickDisputar();
    await detalle.verifyModalOpen('Disputar');

    await page
      .locator('#motivoDisputa')
      .fill('Inconsistencia numérica en los votos de candidatos');
    await page
      .locator('[role="dialog"] button')
      .filter({ hasText: /Confirmar Disputa/ })
      .last()
      .click();

    await expect(page.locator('app-badge').first()).toContainText('En Disputa', {
      timeout: 15_000,
    });

    // --- 2. El observer corrige el acta ---
    await loginReal(page, 'OBSERVER');
    await page.goto(`/observer/acta/${actaId}/corregir`);

    await expect(page.getByTestId('form-card')).toBeVisible({
      timeout: 15_000,
    });
    // El motivo de la disputa le llega al observer.
    await expect(page.getByTestId('dispute-banner')).toBeVisible();

    // Votos que cuadran con el padron.
    await page.getByTestId('input-total-sufragantes').fill('10');
    await page.getByTestId('input-votos-blancos').fill('1');
    await page.getByTestId('input-votos-nulos').fill('1');

    const camposCandidato = page.getByTestId('input-votos-candidato');
    const totalCandidatos = await camposCandidato.count();
    for (let i = 0; i < totalCandidatos; i++) {
      await camposCandidato.nth(i).fill(String(i === 0 ? 8 : 0));
    }

    await expect(page.getByTestId('checksum-valid')).toBeVisible({
      timeout: 10_000,
    });

    await page.getByTestId('btn-enviar-correccion').click();

    await expect(page.getByTestId('success-message')).toBeVisible({
      timeout: 15_000,
    });

    // --- 3. El coordinator aprueba el acta corregida ---
    await loginReal(page, 'COORDINATOR');
    await detalle.goto(actaId);

    await expect(page.locator('app-badge').first()).toContainText('Corregida', {
      timeout: 15_000,
    });

    await detalle.clickAprobar();
    await detalle.verifyModalOpen('Aprobar');
    await detalle.clickModalConfirm();

    await expect(page.locator('.success-banner')).toContainText('APROBADA', {
      timeout: 15_000,
    });
  });
});
