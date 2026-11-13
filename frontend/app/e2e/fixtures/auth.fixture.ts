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
import { mockLegalVersion } from '../support/legal-mocks';
import { versionTerminosVigente, scriptTerminosAceptados, CLAVE_TERMINOS_ACEPTADOS } from '../support/consent';

type UserRole = 'MASTER' | 'OBSERVER' | 'COORDINATOR';

const USERS = {
  MASTER: MASTER_USER,
  OBSERVER: OBSERVER_USER,
  COORDINATOR: COORDINATOR_USER,
};

/**
 * Usuarios reales del seed, para los specs que hablan con la API de verdad.
 *
 * <p>La password es la misma para todos: es el hash que siembran las
 * migraciones `025-data-seed.sql` y `026-admin-inicial.sql`.
 *
 * <p>Ojo con el MASTER: `admsismas@control.ttcs` era el admin que creaba el
 * `DataSeeder` del backend Spring. En Quarkus ese seeder ya no existe —lo
 * sustituyo la migracion `026-admin-inicial.sql`— y el master sembrado es
 * `admin@control-elector.local`. Con el correo viejo el login devuelve 401.
 */
export const USUARIOS_SEED: Record<UserRole, { email: string; password: string }> = {
  MASTER: { email: 'admin@control-elector.local', password: 'admin12345' },
  OBSERVER: { email: 'observervera@example.local', password: 'admin12345' },
  COORDINATOR: {
    email: 'coordinatorcortes@example.local',
    password: 'admin12345',
  },
};

/**
 * Pre-authenticates a page as the specified role.
 *
 * Flow:
 * 1. Register all API mocks BEFORE any navigation (critical for route interception)
 * 2. Register init script to seed terms acceptance (prevents modal in mocked flow)
 * 3. Navigate to / (Angular initializes, guard checks session)
 * 4. authGuard detects empty store, calls restoreSession() → /auth/refresh (mocked)
 * 5. /auth/refresh returns a valid session, AuthStore populates
 * 6. Guard allows navigation, page may redirect based on role
 * 7. Test can then navigate to protected routes (mocks already registered)
 *
 * Why seed sessionStorage for terms?
 * - The app checks sessionStorage['ce.terms.accepted'] to decide if modal appears.
 * - Even though /auth/refresh mocks acceptedTermsVersion, the app ALSO checks sessionStorage.
 * - Seeding here prevents the modal from blocking mocked tests.
 * - J6 (legal-terms) doesn't use loginAs(), so it won't seed and the modal will appear.
 *
 * Why not inject into localStorage?
 * - AuthStore never reads from localStorage (token lives only in-memory)
 * - On page reload, the guard MUST call /auth/refresh to restore the session
 * - E2E simulates this by having an empty store after page load
 */
async function loginAs(page: Page, rol: UserRole): Promise<void> {
  const user = USERS[rol];
  const versionTerminos = versionTerminosVigente();

  // Register mocks BEFORE any navigation (critical for route interception)
  await mockAuthRefresh(page, user);
  await mockAuthMe(page, user);
  await mockMenusMe(page, rol);
  // La version vigente se mockea con el mismo valor que viaja en la sesion: si
  // difieren, el guard cree que falta la aceptacion y manda a `/terminos`.
  await mockLegalVersion(page, versionTerminos);

  // Inject script to seed sessionStorage BEFORE navigation (prevents terms modal in mocked flow)
  await page.addInitScript(scriptTerminosAceptados(versionTerminos));

  // Navigate to app root — triggers authGuard to restore session via /auth/refresh
  // Mocks are in place; auth guard will call /auth/refresh (mocked) and populate session
  await page.goto('/', { waitUntil: 'networkidle', timeout: 15_000 });

  // AuthStore is now populated. Subsequent test navigations will pass the guard.
}

/**
 * Entra a la app como un usuario REAL, contra la API real.
 *
 * <p>A diferencia de {@link loginAs}, aqui no hay mocks: se usa el formulario
 * de login, de modo que la cookie httpOnly de refresh queda en el navegador y
 * el flujo es el mismo que el de un veedor en un recinto.
 *
 * <p>Si el usuario todavia no acepto los terminos vigentes, el guard lo manda
 * a `/terminos` y el modal aparece: se acepta a mano, que es exactamente lo
 * que hace una persona.
 *
 * @param page pagina con la que trabajar
 * @param rol rol del usuario del seed
 */
export async function loginReal(page: Page, rol: UserRole): Promise<void> {
  const usuario = USUARIOS_SEED[rol];

  await page.goto('/login');
  // Selectores por id: `getByLabel(/contraseña/)` tambien encuentra el boton
  // de "Mostrar contraseña" y Playwright falla por ambiguedad.
  await page.locator('#email').fill(usuario.email);
  await page.locator('#password').fill(usuario.password);
  await page.getByRole('button', { name: /ingresar|login|entrar/i }).click();

  await aceptarTerminosSiAparecen(page);
}

/**
 * Acepta los terminos si el modal aparece.
 *
 * <p>Es idempotente: si no hay modal, no hace nada. Existe para los specs
 * que hablan con la API real, donde la aceptacion es un hecho que el backend
 * registra y no algo que el test pueda sembrar.
 *
 * @param page pagina con la que trabajar
 */
export async function aceptarTerminosSiAparecen(page: Page): Promise<void> {
  const boton = page.getByTestId('terms-accept');
  try {
    await boton.waitFor({ state: 'visible', timeout: 5_000 });
  } catch {
    return;
  }
  await boton.click();
  await page.waitForURL((url) => !url.pathname.includes('/terminos'), {
    timeout: 15_000,
  });
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
