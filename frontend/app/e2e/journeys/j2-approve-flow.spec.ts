import { test, expect, Page } from '@playwright/test';
import { ActasListaPage, ActaDetallePage } from '../fixtures/pages';

/**
 * T-FE-10 J2: Approve Flow
 *
 * User journey:
 * 1. Navigate to actas list
 * 2. Click acta in ENVIADA state
 * 3. Open Aprobar modal
 * 4. Confirm approval
 * 5. Verify toast message
 * 6. Verify estado changed to APROBADA
 */

test.describe('J2: Approve Acta Flow', () => {
  let page: Page;
  let lista: ActasListaPage;
  let detalle: ActaDetallePage;

  test.beforeEach(async ({ browser }) => {
    page = await browser.newPage();
    lista = new ActasListaPage(page);
    detalle = new ActaDetallePage(page);
    await lista.goto();
  });

  test.afterEach(async () => {
    await page.close();
  });

  test('should approve an acta in ENVIADA state', async () => {
    // Filter to ENVIADA
    await lista.clickEstadoTab('Enviadas');
    await page.waitForTimeout(500);

    // Get first acta ID (in real test, from mocked API response)
    // For now, click first row
    await lista.clickActaRow(0);

    // Verify detail page loaded
    const title = page.locator('h1');
    await expect(title).toBeVisible();

    // Click Aprobar button
    await detalle.clickAprobar();

    // Verify modal opened
    await detalle.verifyModalOpen('Aprobar');

    // Click confirm
    await detalle.clickModalConfirm();

    // Verify modal closed
    await detalle.verifyModalClosed();

    // Verify toast (success message)
    await lista.verifyToastMessage('Acta aprobada');

    // Verify status changed to APROBADA
    const status = await detalle.getActaStatus();
    expect(status).toContain('APROBADA');
  });
});
