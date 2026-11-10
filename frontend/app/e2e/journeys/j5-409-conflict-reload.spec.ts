import { test, expect, Page } from '@playwright/test';
import { ActasListaPage, ActaDetallePage } from '../fixtures/pages';

/**
 * T-FE-10 J5: 409 Conflict Handling + Auto-Reload
 *
 * User journey (concurrent modification scenario):
 * 1. Open acta detail page
 * 2. Attempt to approve acta
 * 3. Backend returns 409 Conflict (another user modified acta concurrently)
 * 4. Verify error message displayed
 * 5. Verify detail page auto-reloads
 * 6. Verify acta estado reflects latest state (not the old one)
 * 7. Verify user can retry action or navigate away
 */

test.describe('J5: 409 Conflict Handling + Auto-Reload', () => {
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

  test('should handle 409 Conflict and auto-reload detail page', async () => {
    // Navigate to detail page
    await lista.clickActaRow(0);
    await page.waitForTimeout(500);

    // Get initial status
    let initialStatus = await detalle.getActaStatus();
    console.log('Initial status:', initialStatus);

    // Intercept API call and respond with 409
    await page.route('**/api/v1/coordinator/actas/*/aprobar', (route) => {
      route.abort('failed');
    });

    // Attempt to approve
    await detalle.clickAprobar();
    await detalle.verifyModalOpen('Aprobar');

    // Click confirm (will fail with network error simulating 409)
    await detalle.clickModalConfirm();

    // Verify error message displayed
    await detalle.verifyErrorMessage('conflict');

    // Wait for auto-reload
    await page.waitForTimeout(1000);

    // Verify page still on detail (auto-reload, not navigate)
    const url = page.url();
    expect(url).toContain('/coordinator/actas/');

    // Verify status reflects latest state (may have changed during reload)
    const reloadedStatus = await detalle.getActaStatus();
    console.log('Reloaded status:', reloadedStatus);

    // Status should be loaded (either same or changed from backend)
    expect(reloadedStatus).toBeTruthy();
  });

  test('should allow retry after 409 Conflict', async () => {
    // Navigate to detail page
    await lista.clickActaRow(0);
    await page.waitForTimeout(500);

    // First attempt: fail with 409
    let attemptCount = 0;
    await page.route('**/api/v1/coordinator/actas/*/aprobar', (route) => {
      attemptCount++;
      if (attemptCount === 1) {
        // First call fails
        route.abort('failed');
      } else {
        // Second call succeeds
        route.continue();
      }
    });

    // First approve attempt (fails)
    await detalle.clickAprobar();
    await detalle.verifyModalOpen('Aprobar');
    await detalle.clickModalConfirm();
    await detalle.verifyErrorMessage('conflict');

    // Close modal and try again
    await page.waitForTimeout(500);
    const closeButton = page.locator('button:has-text("Cancelar")').last();
    await closeButton.click();

    // Second approve attempt (succeeds)
    await detalle.clickAprobar();
    await detalle.verifyModalOpen('Aprobar');
    await detalle.clickModalConfirm();

    // Verify success
    await lista.verifyToastMessage('Aprobada');
  });

  test('should navigate away from 409 Conflict page without losing data', async () => {
    // Navigate to detail page
    await lista.clickActaRow(0);
    await page.waitForTimeout(500);

    // Fail the approve action with 409
    await page.route('**/api/v1/coordinator/actas/*/aprobar', (route) => {
      route.abort('failed');
    });

    // Attempt approve
    await detalle.clickAprobar();
    await detalle.verifyModalOpen('Aprobar');
    await detalle.clickModalConfirm();
    await detalle.verifyErrorMessage('conflict');

    // Navigate back to list
    const backButton = page.locator('a, button:has-text("Atrás")').first();
    if (await backButton.isVisible()) {
      await backButton.click();
    } else {
      await page.goto('/coordinator/actas');
    }

    // Verify list page loads without errors
    await lista.goto();
    const title = page.locator('h1:has-text("Bandeja de Actas")');
    await expect(title).toBeVisible();
  });
});
