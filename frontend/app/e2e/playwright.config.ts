import { defineConfig, devices } from '@playwright/test';

/**
 * T-FE-10 Phase 2: E2E Tests for Coordinator Actas
 *
 * 5 journeys:
 * - J1: Load/Filter/Paginate
 * - J2: Approve Flow
 * - J3: Dispute→Correct→Approve
 * - J4: Anular (MASTER-only)
 * - J5: 409 Conflict + Reload
 */

export default defineConfig({
  testDir: './e2e/journeys',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
  ],

  /* Run your local dev server before starting the tests */
  webServer: {
    command: 'npm run start',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env.CI,
  },
});
