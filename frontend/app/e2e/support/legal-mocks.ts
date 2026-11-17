import { Page } from '@playwright/test';

/**
 * Base URL de la API tal como la ve la app (coincide con environment.apiUrl).
 */
const API = '**/api/v1';

/**
 * Mocks del bloque legal (T-LEGAL-01).
 *
 * <p>La version vigente y el registro de aceptacion son los unicos endpoints que
 * el flujo de aceptacion necesita. Se mockean para que el E2E verifique el
 * COMPORTAMIENTO del frontend —el gate, el modal, el cierre de sesion— y no el
 * backend, que ya tiene su propia suite de integracion.
 */

/**
 * Version vigente de los documentos legales.
 */
export async function mockLegalVersion(
  page: Page,
  version = '1.0',
): Promise<void> {
  await page.route(`${API}/legal/current-version`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        version,
        terms_version: version,
        privacy_version: version,
      }),
    });
  });
}

/**
 * Registro de la aceptacion de terminos.
 *
 * @returns el numero de veces que se llamo al endpoint
 */
export async function mockAcceptTerms(
  page: Page,
  version = '1.0',
): Promise<{ calls: () => number }> {
  let calls = 0;
  await page.route(`${API}/user/accept-terms`, async (route) => {
    calls += 1;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'accepted',
        version,
        accepted_at: new Date().toISOString(),
      }),
    });
  });
  return { calls: () => calls };
}

/**
 * Cierre de sesion: el logout tiene que llamarse cuando el modal se cierra
 * sin aceptar.
 */
export async function mockLogout(page: Page): Promise<{ calls: () => number }> {
  let calls = 0;
  await page.route(`${API}/auth/logout`, async (route) => {
    calls += 1;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({}),
    });
  });
  return { calls: () => calls };
}

/**
 * Login que devuelve la version de terminos aceptada.
 *
 * <p>El `usuario` del body es lo que la app guarda en el store: si no viene
 * `acceptedTermsVersion`, la app cree que el usuario nunca acepto y el modal
 * aparece siempre.
 */
export async function mockAuthLoginWithTerms(
  page: Page,
  token: string,
  email: string,
  rol: string,
  acceptedTermsVersion: string | null,
): Promise<void> {
  await page.route(`${API}/auth/login`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: token,
        tokenType: 'Bearer',
        expiresInSeconds: 900,
        sesionId: 'e2e-legal-session',
        usuario: {
          id: 'e2e-legal-user',
          email,
          rol,
          acceptedTermsVersion,
        },
      }),
    });
  });
}
