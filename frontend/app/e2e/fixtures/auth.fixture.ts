import { test as base, Page } from '@playwright/test';
import {
  mockAuthLogin,
  mockAuthMe,
  mockAuthRefresh,
  mockMenusMe,
  MASTER_USER,
  OBSERVER_USER,
  COORDINATOR_USER,
  TOKENS,
} from '../support/api-mocks';

type UserRole = 'MASTER' | 'OBSERVER' | 'COORDINATOR';

const USERS = {
  MASTER: MASTER_USER,
  OBSERVER: OBSERVER_USER,
  COORDINATOR: COORDINATOR_USER,
};

/**
 * Pre-authenticates a page as the specified role.
 *
 * Flow:
 * 1. Register all API mocks BEFORE any navigation (critical for route interception)
 * 2. Navigate to / (Angular initializes, guard checks session)
 * 3. authGuard detects empty store, calls restoreSession() → /auth/refresh (mocked)
 * 4. /auth/refresh returns a valid session, AuthStore populates
 * 5. Guard allows navigation, page may redirect based on role
 * 6. Test can then navigate to protected routes (mocks already registered)
 *
 * Why not inject into localStorage?
 * - AuthStore never reads from localStorage (token lives only in-memory)
 * - On page reload, the guard MUST call /auth/refresh to restore the session
 * - E2E simulates this by having an empty store after page load
 */
async function loginAs(page: Page, rol: UserRole): Promise<void> {
  const user = USERS[rol];

  // Register mocks BEFORE any navigation (critical for route interception)
  await mockAuthRefresh(page, user);
  await mockAuthMe(page, user);
  await mockMenusMe(page, rol);

  // Navigate to app root — triggers authGuard to restore session via /auth/refresh
  // Mocks are in place; auth guard will call /auth/refresh (mocked) and populate session
  await page.goto('/', { waitUntil: 'networkidle', timeout: 15_000 });

  // AuthStore is now populated. Subsequent test navigations will pass the guard.
}

// ---------------------------------------------------------------------------
// Extended fixture types
// ---------------------------------------------------------------------------

export type AuthFixtures = {
  /** Page pre-authenticated as OBSERVER */
  observerPage: Page;
  /** Page pre-authenticated as COORDINATOR */
  coordinatorPage: Page;
  /** Page pre-authenticated as MASTER */
  masterPage: Page;
  /** Raw page with no auth — for login-flow tests */
  guestPage: Page;
};

// ---------------------------------------------------------------------------
// Extended test object
// ---------------------------------------------------------------------------

export const test = base.extend<AuthFixtures>({
  observerPage: async ({ page }, use) => {
    await loginAs(page, 'OBSERVER');
    await use(page);
  },

  coordinatorPage: async ({ page }, use) => {
    await loginAs(page, 'COORDINATOR');
    await use(page);
  },

  masterPage: async ({ page }, use) => {
    await loginAs(page, 'MASTER');
    await use(page);
  },

  guestPage: async ({ page }, use) => {
    // No token — fresh page
    await use(page);
  },
});

// ---------------------------------------------------------------------------
// Re-export expect so spec files only import from this module
// ---------------------------------------------------------------------------

export { expect } from '@playwright/test';

// ---------------------------------------------------------------------------
// Login helper (for tests that exercise the UI login flow itself)
// ---------------------------------------------------------------------------

export async function loginViaUi(
  page: Page,
  email: string,
  password: string,
): Promise<void> {
  await mockAuthLogin(page, USERS.MASTER); // mock the POST /auth/login
  await page.goto('/login');
  await page.getByLabel(/correo|email/i).fill(email);
  await page.getByLabel(/contraseña|password/i).fill(password);
  await page.getByRole('button', { name: /ingresar|login|entrar/i }).click();
}
