import { test, expect, Page } from '@playwright/test';
import { ActasListaPage, ActaDetallePage } from '../fixtures/pages';

/**
 * T-FE-10 J3: Dispute → Correct → Approve
 *
 * User journey:
 * 1. Navigate to acta in ENVIADA state
 * 2. Dispute acta (click Disputar → fill motivo → confirm)
 * 3. Verify estado changed to EN_DISPUTA
 * 4. Correct acta (click Corregir → adjust votes → verify checksum → confirm)
 * 5. Verify estado changed to CORREGIDA
 * 6. Approve acta
 * 7. Verify estado changed to APROBADA
 */

test.describe('J3: Dispute → Correct → Approve Flow', () => {
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

  test('should complete dispute → correct → approve workflow', async () => {
    // Navigate to detail page
    await lista.clickActaRow(0);
    await page.waitForTimeout(500);

    // === DISPUTE ===
    await detalle.clickDisputar();
    await detalle.verifyModalOpen('Disputar');

    // Fill motivo
    await detalle.fillModalInput('Motivo de la Disputa', 'Inconsistencia numérica');

    // Confirm dispute
    await detalle.clickModalConfirm();
    await detalle.verifyModalClosed();

    // Verify toast
    await lista.verifyToastMessage('Acta en disputa');

    // Verify status changed to EN_DISPUTA
    let status = await detalle.getActaStatus();
    expect(status).toContain('EN_DISPUTA');

    // === CORRECT ===
    await page.waitForTimeout(500);
    await detalle.clickCorregir();
    await detalle.verifyModalOpen('Corregir');

    // Fill correction votes (adjust first candidate)
    await detalle.fillModalInput('Votos Candidato 1', '50');

    // Verify checksum validates
    await detalle.verifyChecksumValid();

    // Confirm correction
    await detalle.clickModalConfirm();
    await detalle.verifyModalClosed();

    // Verify toast
    await lista.verifyToastMessage('Acta corregida');

    // Verify status changed to CORREGIDA
    status = await detalle.getActaStatus();
    expect(status).toContain('CORREGIDA');

    // === APPROVE ===
    await page.waitForTimeout(500);
    await detalle.clickAprobar();
    await detalle.verifyModalOpen('Aprobar');

    // Confirm approval
    await detalle.clickModalConfirm();
    await detalle.verifyModalClosed();

    // Verify toast
    await lista.verifyToastMessage('Acta aprobada');

    // Verify final status APROBADA
    status = await detalle.getActaStatus();
    expect(status).toContain('APROBADA');
  });
});
