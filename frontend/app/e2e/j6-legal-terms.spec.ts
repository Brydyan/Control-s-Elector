import { test, expect } from '@playwright/test';
import {
  mockLegalVersion,
  mockAcceptTerms,
  mockLogout,
  mockAuthLoginWithTerms,
} from './support/legal-mocks';
import {
  mockMenusMe,
  mockObserverJrvs,
  TOKENS,
  OBSERVER_USER,
} from './support/api-mocks';

/**
 * J6 — Aceptación de términos (T-LEGAL-01)
 *
 * Cubre el recorrido completo de la aceptación:
 *
 *   1. Login de un usuario que NO ha aceptado → aparece el modal.
 *   2. "Acepto" → POST /api/v1/user/accept-terms → el usuario entra al sistema.
 *   3. Login de un usuario que YA aceptó la versión vigente → no hay modal.
 *   4. Cerrar el modal sin aceptar → se cierra la sesión y se vuelve al login.
 *
 * API mocks:
 *   GET  /api/v1/legal/current-version → { version: "1.0" }
 *   POST /api/v1/user/accept-terms    → 200 { status: "accepted" }
 *   POST /api/v1/auth/logout           → 200
 *   POST /api/v1/auth/login            → sesión con acceptedTermsVersion
 *   GET  /api/v1/menus/me              → menú del rol
 *   GET  /api/v1/observer/jrvs         → JRVs del observador
 */
test.describe('J6 — Aceptación de términos (T-LEGAL-01)', () => {
  const EMAIL = OBSERVER_USER.email;
  const PASSWORD = OBSERVER_USER.password;

  async function entrarComo(page: import('@playwright/test').Page, accepted: string | null) {
    await mockLegalVersion(page, '1.0');
    await mockMenusMe(page, 'OBSERVER');
    await mockObserverJrvs(page);
    await mockAuthLoginWithTerms(
      page,
      TOKENS.OBSERVER,
      EMAIL,
      'OBSERVER',
      accepted,
    );

    await page.goto('/login');
    // Selectores por id: `getByLabel(/contraseña/)` también encuentra el boton
    // de "Mostrar contraseña" y Playwright falla por ambiguedad.
    await page.locator('#email').fill(EMAIL);
    await page.locator('#password').fill(PASSWORD);
    await page.getByRole('button', { name: /ingresar|login|entrar/i }).click();
  }

  test('un usuario sin aceptar ve el modal y al aceptar entra al sistema', async ({
    page,
  }) => {
    const accept = await mockAcceptTerms(page, '1.0');
    await entrarComo(page, null);

    // El modal aparece: es la pantalla que no se puede saltar.
    await expect(
      page.getByText('Términos de Uso Actualizados'),
    ).toBeVisible();
    // El host del <p-dialog> no tiene caja: el dialogo visible es el del overlay.
    await expect(page.getByRole('dialog')).toBeVisible();

    // Y ofrece los dos documentos para leer.
    await expect(page.getByTestId('link-terms')).toHaveAttribute('href', '/terms');
    await expect(page.getByTestId('link-privacy')).toHaveAttribute(
      'href',
      '/privacy',
    );

    await page.getByTestId('terms-accept').click();

    // El backend registró la aceptación y la app dejó entrar.
    await expect.poll(() => accept.calls()).toBe(1);
    await expect(page).toHaveURL(/\/observer\/mis-jrvs/);
    await expect(
      page.getByText('Términos de Uso Actualizados'),
    ).toBeHidden();
  });

  test('un usuario que ya aceptó la versión vigente entra sin modal', async ({
    page,
  }) => {
    const accept = await mockAcceptTerms(page, '1.0');
    await entrarComo(page, '1.0');

    await expect(page).toHaveURL(/\/observer\/mis-jrvs/);
    await expect(page.getByText('Términos de Uso Actualizados')).toHaveCount(0);
    expect(accept.calls()).toBe(0);
  });

  test('cerrar el modal sin aceptar cierra la sesión y vuelve al login', async ({
    page,
  }) => {
    const accept = await mockAcceptTerms(page, '1.0');
    const logout = await mockLogout(page);
    await entrarComo(page, null);

    await expect(page.getByText('Términos de Uso Actualizados')).toBeVisible();

    // Cerrar sin aceptar: Escape es la via de salida del dialog.
    await page.keyboard.press('Escape');

    await expect.poll(() => logout.calls()).toBe(1);
    await expect(page).toHaveURL(/\/login/);
    expect(accept.calls()).toBe(0);
  });
});
