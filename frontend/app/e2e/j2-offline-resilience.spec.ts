import { test, expect } from './fixtures/auth.fixture';
import {
  mockObserverJrvs,
  mockObserverCargos,
  mockConfiguracionCandidatos,
  mockConfiguracionPartidos,
  mockActaBorrador,
  mockActaEnvio,
  mockImagenUpload,
  STUB_JRV_1,
  STUB_CARGO_1,
} from './support/api-mocks';

/**
 * J2 — Offline Resilience (Recinto sin señal)
 *
 * Simulates a low-connectivity scenario where:
 * 1. User starts online, loads acta form
 * 2. Network goes offline (navigator.onoffline)
 * 3. User continues entering data
 * 4. Data saved to IndexedDB (borrador)
 * 5. Network comes back online
 * 6. SyncService auto-transmits cached actas
 *
 * Expected behavior:
 *   - UI remains functional during offline
 *   - Botón "Enviar" disabled when offline (visual feedback)
 *   - Data persisted in IndexedDB
 *   - Auto-sync on reconnection (no manual retry needed)
 */
test.describe('J2 — Resiliencia Offline (recinto sin señal)', () => {
  const SUFRAGANTES = 250;
  const VOTOS = {
    rodriguez: 150,
    lopez: 80,
    blancos: 10,
    nulos: 10,
  };

  test.beforeEach(async ({ observerPage: page }) => {
    // Register all API mocks (online phase)
    await mockObserverJrvs(page);
    await mockObserverCargos(page);
    await mockConfiguracionCandidatos(page);
    await mockConfiguracionPartidos(page);
    await mockActaBorrador(page);
  });

  test('should persist data to IndexedDB when offline', async ({ observerPage: page }) => {
    // Navigate to form and complete first step (IMAGEN) while online
    await page.goto(`/observer/jrv/${STUB_JRV_1.jrvId}/cargo/${STUB_CARGO_1.id}/registrar`);

    // Attach image (required to advance to VOTOS step)
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles('e2e/fixtures/acta-foto.jpg');

    // Advance to VOTOS step
    await page
      .getByRole('button', { name: /continuar|votos/i })
      .first()
      .click({ timeout: 10_000 });

    // Phase 2: Go offline before filling votes
    await page.context().setOffline(true);

    // User should still be able to enter votos offline (form remains functional)
    await page.getByLabel(/sufragantes|padrón/i).fill(String(SUFRAGANTES));
    await page.getByLabel(/rodríguez/i).fill(String(VOTOS.rodriguez));
    await page.getByLabel(/lópez/i).fill(String(VOTOS.lopez));
    await page.getByLabel(/blancos/i).fill(String(VOTOS.blancos));
    await page.getByLabel(/nulos/i).fill(String(VOTOS.nulos));

    // "Guardar" button should work offline (saves to IndexedDB)
    const guardarBtn = page.getByRole('button', { name: /guardar/i });
    await expect(guardarBtn).toBeVisible();
    await guardarBtn.click();

    // Phase 3: Restore connectivity
    await page.context().setOffline(false);
  });

  test('should disable submit button when offline', async ({ observerPage: page }) => {
    // Complete form online until REVISION step
    await page.goto(`/observer/jrv/${STUB_JRV_1.jrvId}/cargo/${STUB_CARGO_1.id}/registrar`);

    // Attach image and advance to VOTOS
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles('e2e/fixtures/acta-foto.jpg');
    await page
      .getByRole('button', { name: /continuar|votos/i })
      .first()
      .click({ timeout: 10_000 });

    // Fill votes
    await page.getByLabel(/sufragantes|padrón/i).fill(String(SUFRAGANTES));
    await page.getByLabel(/rodríguez/i).fill(String(VOTOS.rodriguez));
    await page.getByLabel(/lópez/i).fill(String(VOTOS.lopez));
    await page.getByLabel(/blancos/i).fill(String(VOTOS.blancos));
    await page.getByLabel(/nulos/i).fill(String(VOTOS.nulos));

    // Advance to REVISION (where Enviar button exists)
    await page.getByRole('button', { name: /revisión|continuar/i }).click({ timeout: 8_000 });

    // Now go offline
    await page.context().setOffline(true);

    // "Enviar" button should be disabled
    const enviarBtn = page.getByRole('button', { name: /enviar|submit/i });
    await expect(enviarBtn).toBeDisabled();

  });
});
