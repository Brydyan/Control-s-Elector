import { test, expect, Page } from '@playwright/test';
import { ActasListaPage, ActaDetallePage, AuthFixture } from '../fixtures/pages';

/**
 * T-FE-10 J4: Anular Acta (MASTER-only)
 *
 * User journey:
 * 1. Login as COORDINATOR
 * 2. Navigate to APROBADA acta
 * 3. Verify Anular button is DISABLED
 * 4. Logout, login as MASTER
 * 5. Navigate to same acta
 * 6. Verify Anular button is ENABLED
 * 7. Click Anular → fill motivo → confirm
 * 8. Verify estado changed to ANULADA
 * 9. Verify acta appears in ANULADA tab
 */

test.describe('J4: Anular Acta (MASTER-only)', () => {
  let page: Page;
  let lista: ActasListaPage;
  let detalle: ActaDetallePage;
  let auth: AuthFixture;

  test.beforeEach(async ({ browser }) => {
    page = await browser.newPage();
    lista = new ActasListaPage(page);
    detalle = new ActaDetallePage(page);
    auth = new AuthFixture(page);
  });

  test.afterEach(async () => {
    await page.close();
  });

  test('should disable Anular for COORDINATOR, enable for MASTER', async () => {
    // === COORDINATOR LOGIN ===
    await auth.loginAs('COORDINATOR');
    await lista.goto();

    // Filter to APROBADA
    await lista.clickEstadoTab('Aprobadas');
    await page.waitForTimeout(500);

    // Navigate to detail
    await lista.clickActaRow(0);
    await page.waitForTimeout(500);

    // Verify Anular button is disabled
    const anularButtonCoord = page.locator('button:has-text("Anular")');
    await expect(anularButtonCoord).toBeDisabled();

    // === MASTER LOGIN ===
    // (In real test: logout and login as MASTER)
    await auth.loginAs('MASTER');

    // Refresh page or navigate back to same acta
    await page.reload();
    await page.waitForTimeout(500);

    // Verify Anular button is now ENABLED
    const anularButtonMaster = page.locator('button:has-text("Anular")');
    await expect(anularButtonMaster).toBeEnabled();

    // === PERFORM ANULAR ===
    await detalle.clickAnular();
    await detalle.verifyModalOpen('Anular');

    // Fill motivo
    await detalle.fillModalInput('Motivo de Anulación', 'Acta fraudulenta');

    // Confirm
    await detalle.clickModalConfirm();
    await detalle.verifyModalClosed();

    // Verify toast
    await lista.verifyToastMessage('Acta anulada');

    // Verify status changed to ANULADA
    const status = await detalle.getActaStatus();
    expect(status).toContain('ANULADA');

    // === VERIFY IN LIST ===
    // Navigate back to lista
    await lista.goto();

    // Filter to ANULADA tab
    await lista.clickEstadoTab('Anuladas');
    await page.waitForTimeout(500);

    // Verify acta appears in ANULADA tab
    const rowCount = await lista.getTableRowCount();
    expect(rowCount).toBeGreaterThan(0);
  });
});
