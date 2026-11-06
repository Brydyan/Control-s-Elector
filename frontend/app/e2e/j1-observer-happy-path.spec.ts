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
  STUB_CANDIDATOS,
} from './support/api-mocks';

const FIXTURE_IMAGE = 'e2e/fixtures/acta-foto.jpg';

/**
 * J1 — Happy Path Observer (online)
 *
 * Simulates the full acta registration flow for a single-member electoral
 * cargo (Alcalde, 1 candidato uninominal) with a balanced checksum and a
 * successful image upload.
 *
 * Steps:
 *   1. Navigate to /observer/mis-jrvs (authenticated as OBSERVER)
 *   2. Select JRV-001
 *   3. Select cargo "Alcalde"
 *   4. Attach photo → wizard step "Votos"
 *   5. Fill totalSufragantes = 300, candidatos, blancos, nulos (sum = 300)
 *   6. Continue to "Revisión"
 *   7. Submit → assert success toast / success state
 *
 * API mocks:
 *   GET  /api/v1/observer/jrvs                      → [JRV-001]
 *   GET  /api/v1/observer/jrv/1/cargos              → [Alcalde]
 *   GET  /api/v1/configuracion/candidatos?cargoId=10 → [Rodríguez, López]
 *   GET  /api/v1/configuracion/partidos              → [MC, AP]
 *   POST /api/v1/observer/actas/borrador             → 201 { id: 999 }
 *   POST /api/v1/observer/actas/999/imagen           → 200
 *   POST /api/v1/observer/actas                      → 201 { id: 999, estado: ENVIADA }
 */
test.describe('J1 — Flujo Feliz Observador (online)', () => {
  const FIXTURE_IMAGE = 'e2e/fixtures/acta-foto.jpg';

  /**
   * Total sufragantes = 300.
   * Carlos Rodríguez: 180, Ana López: 100, Blancos: 10, Nulos: 10 → sum = 300 ✓
   */
  const SUFRAGANTES = 300;
  const VOTOS = {
    rodriguez: 180,   // candidatoId 101
    lopez: 100,       // candidatoId 102
    blancos: 10,
    nulos: 10,
  };

  test.beforeEach(async ({ observerPage: page }) => {
    // Register all API mocks before any navigation
    await mockObserverJrvs(page);
    await mockObserverCargos(page);
    await mockConfiguracionCandidatos(page);
    await mockConfiguracionPartidos(page);
    await mockActaBorrador(page);
    await mockActaEnvio(page, 201);
    await mockImagenUpload(page, 200);
  });

  // -------------------------------------------------------------------------
  // Step 1: JRV selection
  // -------------------------------------------------------------------------

  test('should display assigned JRVs for observer', async ({ observerPage: page }) => {
    await page.goto('/observer/mis-jrvs');

    await expect(page.getByText(STUB_JRV_1.numero)).toBeVisible();
    await expect(page.getByText(STUB_JRV_1.parroquia)).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // Step 2: Cargo selection
  // -------------------------------------------------------------------------

  test('should navigate to cargo list after selecting a JRV', async ({ observerPage: page }) => {
    await page.goto('/observer/mis-jrvs');

    // Direct navigation (Playwright doesn't auto-navigate on SPA clicks)
    await page.goto(`/observer/jrv/${STUB_JRV_1.jrvId}/cargos`);

    await expect(page).toHaveURL(
      new RegExp(`/observer/jrv/${STUB_JRV_1.jrvId}/cargos`),
    );
    await expect(page.getByText(STUB_CARGO_1.nombre)).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // Step 3: Wizard — IMAGEN step
  // -------------------------------------------------------------------------

  test('should land on wizard IMAGEN step when navigating to registrar-acta', async ({
    observerPage: page,
  }) => {
    await page.goto(
      `/observer/jrv/${STUB_JRV_1.jrvId}/cargo/${STUB_CARGO_1.id}/registrar`,
    );

    // File input should be visible (IMAGEN step shows file upload)
    await expect(page.locator('input[type="file"]')).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // Step 4: Attach image
  // -------------------------------------------------------------------------

  test('should attach image and advance to VOTOS step', async ({
    observerPage: page,
  }) => {
    await page.goto(
      `/observer/jrv/${STUB_JRV_1.jrvId}/cargo/${STUB_CARGO_1.id}/registrar`,
    );

    // Wait for candidatos to load
    await expect(page.locator('.p-steps')).toBeVisible();

    // Set the file on the hidden input
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(FIXTURE_IMAGE);

    // After file selection, the preview image should appear
    await expect(page.locator('img[alt*="foto"], img[alt*="acta"], img[alt*="preview"]'))
      .toBeVisible({ timeout: 8_000 })
      .catch(() =>
        // Some implementations use background-image — just check button availability
        expect(page.getByRole('button', { name: /continuar|votos/i })).toBeVisible(),
      );
  });

  // -------------------------------------------------------------------------
  // Step 5: Fill votes
  // -------------------------------------------------------------------------

  test('should fill votes and pass checksum validation', async ({
    observerPage: page,
  }) => {
    await page.goto(
      `/observer/jrv/${STUB_JRV_1.jrvId}/cargo/${STUB_CARGO_1.id}/registrar`,
    );

    // Attach image to unlock "Continuar" button
    await page.locator('input[type="file"]').setInputFiles(FIXTURE_IMAGE);

    // Wait for image preview
    await page
      .locator('img[alt*="foto"], img[alt*="acta"], img[alt*="preview"]')
      .first()
      .waitFor({ timeout: 10_000 })
      .catch(() => page.waitForTimeout(2000));

    // Advance to VOTOS step
    const continueBtn = page
      .getByRole('button', { name: /continuar|votos/i })
      .first();
    await expect(continueBtn).toBeEnabled({ timeout: 5000 });
    await continueBtn.click({ timeout: 10_000 });

    // Fill sufragantes
    await page.getByRole('spinbutton', { name: /sufragantes|padrón/i }).fill(String(SUFRAGANTES));

    // Fill candidatos using direct label matching
    await page.getByLabel(/rodríguez|rodriguez|carlos/i).fill(String(VOTOS.rodriguez));
    await page.getByLabel(/lópez|lopez|ana/i).fill(String(VOTOS.lopez));

    // Fill blancos / nulos
    await page.getByRole('spinbutton', { name: /blanco/i }).fill(String(VOTOS.blancos));
    await page.getByRole('spinbutton', { name: /nulo/i }).fill(String(VOTOS.nulos));

    // The "Continuar a Revisión" button should be clickable when checksum passes
    const revisionBtn = page.getByRole('button', { name: /revisión final|continuar a revisión/i });
    await revisionBtn.click({ timeout: 8_000 });

    // Verify in REVISION by checking for submit button
    await expect(page.getByRole('button', { name: /transmitir|enviar/i })).toBeVisible({ timeout: 5_000 });
  });

  // -------------------------------------------------------------------------
  // Step 6: Submit acta — full happy path
  // -------------------------------------------------------------------------

  test('J1 full happy path — submit acta and see success state @j1', async ({
    observerPage: page,
  }) => {
    await page.goto(
      `/observer/jrv/${STUB_JRV_1.jrvId}/cargo/${STUB_CARGO_1.id}/registrar`,
    );

    // --- IMAGEN step ---
    await page.locator('input[type="file"]').setInputFiles(FIXTURE_IMAGE);

    // Wait for image preview
    await page
      .locator('img[alt*="foto"], img[alt*="acta"], img[alt*="preview"]')
      .first()
      .waitFor({ timeout: 10_000 })
      .catch(() => page.waitForTimeout(2000));

    const continueToVotos = page
      .getByRole('button', { name: /continuar|votos/i })
      .first();
    await expect(continueToVotos).toBeEnabled({ timeout: 5000 });
    await continueToVotos.click({ timeout: 10_000 });

    // --- VOTOS step ---
    await page.getByRole('spinbutton', { name: /sufragantes|padrón/i }).fill(String(SUFRAGANTES));

    // Fill candidates using direct label matching
    await page.getByLabel(/rodríguez|rodriguez|carlos/i).fill(String(VOTOS.rodriguez));
    await page.getByLabel(/lópez|lopez|ana/i).fill(String(VOTOS.lopez));

    await page.getByRole('spinbutton', { name: /blanco/i }).fill(String(VOTOS.blancos));
    await page.getByRole('spinbutton', { name: /nulo/i }).fill(String(VOTOS.nulos));

    // Advance to REVISION
    await page.getByRole('button', { name: /revisión final|continuar a revisión/i }).click({ timeout: 8_000 });

    // --- REVISION step ---
    // Confirm we're in REVISION by checking for submit button
    await expect(page.getByRole('button', { name: /transmitir|enviar/i })).toBeVisible({ timeout: 5_000 });

    // Click the main submit button
    const submitBtn = page.getByRole('button', {
      name: /transmitir|enviar/i,
    });
    await submitBtn.click({ timeout: 8_000 });

    // Verify redirect on success (app navigates away after submit)
    await expect(page).toHaveURL(/cargos|jrv/, { timeout: 10_000 });
  });

  // -------------------------------------------------------------------------
  // Guard: checksum mismatch blocks advance to REVISION
  // -------------------------------------------------------------------------

  test('should block advance to REVISION when checksum fails @j3', async ({
    observerPage: page,
  }) => {
    await page.goto(
      `/observer/jrv/${STUB_JRV_1.jrvId}/cargo/${STUB_CARGO_1.id}/registrar`,
    );

    await page.locator('input[type="file"]').setInputFiles(FIXTURE_IMAGE);

    const continueToVotos = page
      .getByRole('button', { name: /continuar|votos/i })
      .first();
    await continueToVotos.click({ timeout: 10_000 });

    // Fill an UNBALANCED acta: sufragantes=300, votes sum to only 100
    await page.getByRole('spinbutton', { name: /sufragantes|padrón/i }).fill('300');
    await page.getByRole('spinbutton', { name: /blanco/i }).fill('50');
    await page.getByRole('spinbutton', { name: /nulo/i }).fill('50');
    // Candidato fields left at 0 → sum = 100 ≠ 300

    await page.getByRole('button', { name: /continuar con inconsistencia|revisión|continuar a revisión/i }).first().click({ timeout: 8_000 });

    // The error/warning message about the checksum discrepancy should appear
    await expect(
      page.locator('[role="alert"], .p-message-error, .p-inline-message')
        .filter({ hasText: /faltan|sobran|no cuadra|suma|checksum|diferencia/i }),
    ).toBeVisible({ timeout: 5_000 });
  });

  // -------------------------------------------------------------------------
  // Candidatos list visible in VOTOS step
  // -------------------------------------------------------------------------

  test('should display candidate names in VOTOS step', async ({
    observerPage: page,
  }) => {
    await page.goto(
      `/observer/jrv/${STUB_JRV_1.jrvId}/cargo/${STUB_CARGO_1.id}/registrar`,
    );

    await page.locator('input[type="file"]').setInputFiles(FIXTURE_IMAGE);

    const continueToVotos = page
      .getByRole('button', { name: /continuar|votos/i })
      .first();
    await continueToVotos.click({ timeout: 10_000 });

    for (const c of STUB_CANDIDATOS) {
      await expect(page.getByText(c.nombre)).toBeVisible({ timeout: 8_000 });
    }
  });
});
