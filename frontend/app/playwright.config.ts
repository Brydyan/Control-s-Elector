import { defineConfig, devices } from '@playwright/test';

/**
 * Electoral Control E2E — Playwright configuration.
 *
 * Five journeys:
 *   J1 — Happy Path Observer (online)
 *   J2 — Offline Resilience (IndexedDB queue + auto-sync)
 *   J3 — Strict Checksum & Errors (validation + idempotency)
 *   J4 — Dispute & Correction (Coordinator + Observer flow)
 *   J5 — Live Dashboard Monitoring (Admin/Master analytics)
 *
 * Run all:       pnpm test:e2e
 * Run single:    pnpm test:e2e --grep "@j1"
 * UI mode:       pnpm test:e2e:ui
 */
export default defineConfig({
  testDir: './e2e',
  globalSetup: require.resolve('./globalSetup.ts'),
  fullyParallel: false,       // electoral flows have ordering dependencies
  forbidOnly: !!process.env['CI'],
  // ODD-E2E-SHARDED-CI §6.2: sin reintentos. Reintentar un test de la ruta de
  // registro de acta reintenta el REGISTRO, y un reintento puede convertir un
  // defecto de idempotencia en un test verde. Los flakes se salen con
  // cuarentena explicita, no con un reintento silencioso.
  retries: 0,
  workers: 1,                 // single worker: stateful API mocks
  timeout: 45_000,
  expect: { timeout: 10_000 },

  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],

  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    /**
     * Sesion que preparó el global setup.
     *
     * <p>Trae el consentimiento de cookies ya dado y la cookie de refresh del
     * usuario del seed. Sin el consentimiento, el banner de cookies intercepta
     * los clics (`cookie-banner subtree intercepts pointer events`) y ninguna
     * pantalla alcanza a renderizar.
     *
     * <p>Los terminos NO van aqui: el spec J6 prueba el caso de "el usuario
     * todavia no acepto" y necesita que el modal aparezca.
     */
    storageState: 'auth.json',
    trace: 'on-first-retry',
    video: 'on-first-retry',
    screenshot: 'only-on-failure',
    locale: 'es-EC',
    timezoneId: 'America/Guayaquil',
  },

  projects: [
    {
      name: 'Desktop Chrome',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'Mobile Chrome — Pixel 7',
      use: { ...devices['Pixel 7'] },
    },
    {
      name: 'Mobile Safari — iPhone 12',
      use: { ...devices['iPhone 12'] },
    },
  ],

  /**
   * Optional: start the Angular dev server before running tests.
   * Uncomment when running in CI or when the server is not already running.
   *
   * webServer: {
   *   command: 'pnpm start',
   *   url: 'http://localhost:4200',
   *   reuseExistingServer: !process.env['CI'],
   *   timeout: 120_000,
   * },
   */
});
