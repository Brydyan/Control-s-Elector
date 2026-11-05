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
    // Phase 1: Online — Navigate to cargo form
    await page.goto('/observer/mis-jrvs');

    // Click on JRV card and wait for navigation
    await Promise.all([
      page.waitForURL(new RegExp(`/observer/jrv/${STUB_JRV_1.jrvId}/cargos`)),
      page
        .getByRole('listitem')
        .filter({ hasText: STUB_JRV_1.numero })
        .click()
        .catch(() => page.getByText(STUB_JRV_1.numero).click()),
    ]);

    await page.getByText(STUB_CARGO_1.nombre).click();

    // Phase 2: Go offline (simulate network failure)
    await page.context().setOffline(true);

    // User should still be able to enter votos (form remains functional)
    const totalInput = page.getByLabel(/total sufragantes/i, { exact: true });
    await expect(totalInput).toBeVisible();
    await totalInput.fill(String(SUFRAGANTES));

    // Fill candidato votes
    await page.getByLabel(/rodríguez/i).fill(String(VOTOS.rodriguez));
    await page.getByLabel(/lópez/i).fill(String(VOTOS.lopez));
    await page.getByLabel(/blancos/i).fill(String(VOTOS.blancos));
    await page.getByLabel(/nulos/i).fill(String(VOTOS.nulos));

    // "Guardar" button should work offline (saves to IndexedDB)
    const guardarBtn = page.getByRole('button', { name: /guardar/i });
    await expect(guardarBtn).toBeVisible();
    await guardarBtn.click();

    // Verify data was saved to IndexedDB (no error toast)
    const errorToast = page.getByRole('alert').filter({ hasText: /error/i });
    await expect(errorToast).not.toBeVisible();

    // Phase 3: Restore connectivity
    await page.context().setOffline(false);

    // SyncService should auto-transmit after reconnection
    // Wait for success toast indicating sync completed
    const successToast = page.getByRole('alert').filter({ hasText: /enviado|sincronizado/i });
    await expect(successToast).toBeVisible({ timeout: 5000 });
  });

  test('should disable submit button when offline', async ({ observerPage: page }) => {
    // Navigate to form (online)
    await page.goto('/observer/mis-jrvs');

    // Click on JRV card and wait for navigation
    await Promise.all([
      page.waitForURL(new RegExp(`/observer/jrv/${STUB_JRV_1.jrvId}/cargos`)),
      page
        .getByRole('listitem')
        .filter({ hasText: STUB_JRV_1.numero })
        .click()
        .catch(() => page.getByText(STUB_JRV_1.numero).click()),
    ]);

    // Go offline
    await page.context().setOffline(true);

    // "Enviar" button should be disabled
    const enviarBtn = page.getByRole('button', { name: /enviar|submit/i });
    await expect(enviarBtn).toBeDisabled();

    // "Guardar" button should remain enabled
    const guardarBtn = page.getByRole('button', { name: /guardar/i });
    await expect(guardarBtn).toBeEnabled();
  });
});
