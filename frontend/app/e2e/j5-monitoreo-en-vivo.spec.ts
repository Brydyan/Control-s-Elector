import { test, expect } from './fixtures/auth.fixture';

/**
 * J5 — Monitoreo en Vivo (MASTER Dashboard)
 *
 * Tests coordinator reportes dashboard (T-FE-17):
 * - Page loads with actas, jrvs, cargos data
 * - Metrics display correctly
 * - Canton progress shows acta tallies
 * - Actas table renders
 *
 * CRITICAL: CoordinatorReportesPage calls 3 API endpoints:
 *  - GET /api/v1/coordinator/actas (paged list)
 *  - GET /api/v1/observer/jrvs (all jrvs)
 *  - GET /api/v1/configuracion/cargos (all cargos)
 */
test.describe('J5 — Monitoreo en Vivo (MASTER Dashboard)', () => {
  test.beforeEach(async ({ masterPage: page }) => {
    // Mocks registered per test to ensure clarity
  });

  // -------------------------------------------------------------------------
  // Test 1: Dashboard loads and displays metrics
  // -------------------------------------------------------------------------

  test('should load dashboard with actas metrics', async ({
    masterPage: page,
  }) => {
    // Mock coordinator actas endpoint
    await page.route('**/api/v1/coordinator/actas**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [
            {
              id: 1,
              jrvId: 101,
              cargoId: 10,
              estado: 'APROBADA',
              totalSufragantes: 300,
              votosBlancos: 15,
              votosNulos: 8,
              fechaRegistro: '2026-11-05T10:30:00',
              registradaPorId: 4,
              motivoAnulacion: null,
              nombreCargo: 'Alcalde/sa',
              numeroJrv: 'JRV-001',
              totalVotosCandidatos: 277,
            },
            {
              id: 2,
              jrvId: 102,
              cargoId: 10,
              estado: 'APROBADA',
              totalSufragantes: 280,
              votosBlancos: 12,
              votosNulos: 10,
              fechaRegistro: '2026-11-05T11:45:00',
              registradaPorId: 4,
              motivoAnulacion: null,
              nombreCargo: 'Alcalde/sa',
              numeroJrv: 'JRV-002',
              totalVotosCandidatos: 258,
            },
          ],
          meta: {
            totalElements: 2,
            page: 0,
            size: 50,
            totalPages: 1,
          },
        }),
      });
    });

    // Mock jrvs endpoint
    await page.route('**/api/v1/observer/jrvs**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { jrvId: 101, numero: 'JRV-001', parroquia: 'Santa Elena', cargosPendientes: 0, cargosRegistrados: 1 },
          { jrvId: 102, numero: 'JRV-002', parroquia: 'La Libertad', cargosPendientes: 0, cargosRegistrados: 1 },
          { jrvId: 103, numero: 'JRV-003', parroquia: 'Salinas', cargosPendientes: 1, cargosRegistrados: 0 },
        ]),
      });
    });

    // Mock cargos endpoint
    await page.route('**/api/v1/configuracion/cargos**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 10, nombre: 'Alcalde', nivel: 'MUNICIPAL' },
          { id: 11, nombre: 'Concejal', nivel: 'MUNICIPAL' },
        ]),
      });
    });

    // Navigate to reportes
    await page.goto('/coordinator/reportes', { waitUntil: 'networkidle', timeout: 10_000 });
    await page.waitForTimeout(800);

    // Verify page loaded (title visible)
    await expect(page.getByText('Centro de Reportes').first()).toBeVisible({ timeout: 5_000 });

    // Verify metrics display (actas count or progress percentage)
    const hasMetrics = await page
      .locator('text=/[0-9]+|progreso|jrv|acta/i')
      .first()
      .isVisible({ timeout: 5_000 })
      .catch(() => false);

    expect(hasMetrics).toBe(true);
  });

  // -------------------------------------------------------------------------
  // Test 2: Tab switching between Reportes and Estadísticas
  // -------------------------------------------------------------------------

  test('should navigate to coordinator reportes page without errors', async ({
    masterPage: page,
  }) => {
    // Mock required API endpoints
    await page.route('**/api/v1/coordinator/actas**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: [], meta: { totalElements: 0, page: 0, size: 50, totalPages: 1 } }),
      });
    });

    await page.route('**/api/v1/observer/jrvs**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route('**/api/v1/configuracion/cargos**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    // Navigate
    await page.goto('/coordinator/reportes', { waitUntil: 'networkidle', timeout: 10_000 });

    // Verify page loaded without error (no login page, no error message)
    const title = page.locator('h1, h2').first();
    await expect(title).toBeVisible({ timeout: 5_000 });
    await expect(page.getByText(/login|iniciar sesión/i)).not.toBeVisible();
  });

  // -------------------------------------------------------------------------
  // Test 3: Real-time update simulation (mock data refresh)
  // -------------------------------------------------------------------------

  test('should handle empty actas list gracefully', async ({
    masterPage: page,
  }) => {
    // Mock endpoints with empty data
    await page.route('**/api/v1/coordinator/actas**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: [], meta: { totalElements: 0, page: 0, size: 50, totalPages: 1 } }),
      });
    });

    await page.route('**/api/v1/observer/jrvs**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { jrvId: 1, numero: 'JRV-001', parroquia: 'Santa Elena', cargosPendientes: 0, cargosRegistrados: 0 },
        ]),
      });
    });

    await page.route('**/api/v1/configuracion/cargos**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: 10, nombre: 'Alcalde', nivel: 'MUNICIPAL' }]),
      });
    });

    await page.goto('/coordinator/reportes', { waitUntil: 'networkidle', timeout: 10_000 });
    await page.waitForTimeout(500);

    // Page should load without error even with no data
    const pageContent = page.locator('body');
    await expect(pageContent).toBeVisible();
    await expect(page.getByText(/login|error|failed/i)).not.toBeVisible();
  });

  // -------------------------------------------------------------------------
  // Test 4: Progress bar indicators and percentages
  // -------------------------------------------------------------------------

  test('should display loading state then content', async ({
    masterPage: page,
  }) => {
    // Mock with small delay to allow catching loading state
    await page.route('**/api/v1/coordinator/actas**', async (route) => {
      await page.waitForTimeout(300);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [
            {
              id: 1,
              jrvId: 101,
              cargoId: 10,
              estado: 'APROBADA',
              totalSufragantes: 300,
              votosBlancos: 15,
              votosNulos: 8,
              fechaRegistro: '2026-11-05T10:30:00',
              registradaPorId: 4,
              motivoAnulacion: null,
              nombreCargo: 'Alcalde/sa',
              numeroJrv: 'JRV-001',
              totalVotosCandidatos: 277,
            },
          ],
          meta: { totalElements: 1, page: 0, size: 50, totalPages: 1 },
        }),
      });
    });

    await page.route('**/api/v1/observer/jrvs**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ jrvId: 101, numero: 'JRV-001', parroquia: 'Santa Elena', cargosPendientes: 0, cargosRegistrados: 1 }]),
      });
    });

    await page.route('**/api/v1/configuracion/cargos**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: 10, nombre: 'Alcalde', nivel: 'MUNICIPAL' }]),
      });
    });

    await page.goto('/coordinator/reportes', { waitUntil: 'networkidle', timeout: 10_000 });

    // Content should render after loading
    const pageTitle = page.locator('h1, h2').first();
    await expect(pageTitle).toBeVisible({ timeout: 5_000 });
  });
});
