import { Page } from 'playwright';

/**
 * Base URL de la API tal como la ve la app (coincide con environment.apiUrl).
 */
const API = '**/api/v1';

/**
 * Mocks del flujo de invitacion de una persona (T-AUTH-INV-02).
 */

/**
 * Lista de usuarios del panel de administracion.
 *
 * <p>El panel la pide al abrir; sin esta mock la tabla queda en estado de error y el dialogo de
 * creacion no llega a verse.
 */
export async function mockAdminUsuarios(page: Page): Promise<void> {
  await page.route(`${API}/admin/usuarios*`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: [],
        meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
      }),
    });
  });
}

/**
 * Invitacion de una persona.
 *
 * @returns el numero de veces que se llamo al endpoint
 */
export async function mockInvitarUsuario(
  page: Page,
  respuesta?: {
    usuarioId?: string;
    username?: string;
    emailPersonal?: string;
    status?: string;
  },
): Promise<{ calls: () => number }> {
  let calls = 0;
  await page.route(`${API}/users/invite`, async (route) => {
    calls += 1;
    const cuerpo = route.request().postDataJSON() ?? {};
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        usuarioId: respuesta?.usuarioId ?? 'e2e-invitado-uuid',
        username: respuesta?.username ?? 'juagarobs@control.ttcs',
        emailPersonal:
          respuesta?.emailPersonal ?? cuerpo.emailPersonal ?? 'juan@example.com',
        status: respuesta?.status ?? 'INVITED',
        expiraEn: new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString(),
      }),
    });
  });
  return { calls: () => calls };
}
