import { Page } from '@playwright/test';

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
export async function mockAdminUsuarios(
  page: Page,
  usuarios: unknown[] = [],
): Promise<void> {
  await page.route(`${API}/admin/usuarios*`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: usuarios,
        meta: {
          page: 0,
          size: 20,
          totalElements: usuarios.length,
          totalPages: 1,
        },
      }),
    });
  });
}

/**
 * Reenvio de la invitacion de una cuenta.
 *
 * @returns el numero de veces que se llamo al endpoint
 */
export async function mockResendInvitation(
  page: Page,
): Promise<{ calls: () => number }> {
  let calls = 0;
  await page.route(`${API}/users/*/resend-invitation`, async (route) => {
    calls += 1;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        message: 'Invitación re-enviada a juan@example.com',
        newInvitationSentAt: new Date().toISOString(),
        expiresAt: new Date(Date.now() + 24 * 3600 * 1000).toISOString(),
        emailPersonal: 'juan@example.com',
      }),
    });
  });
  return { calls: () => calls };
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
    // El toggle decide que responde el backend: con el correo apagado la cuenta
    // queda CREATED y sin invitacion que vencer.
    const sendEmail = cuerpo.sendEmail !== false;
    const emailPersonal =
      respuesta?.emailPersonal ?? cuerpo.emailPersonal ?? 'juan@example.com';
    const status = respuesta?.status ?? (sendEmail ? 'INVITED' : 'CREATED');
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        usuarioId: respuesta?.usuarioId ?? 'e2e-invitado-uuid',
        username: respuesta?.username ?? 'juagarobs@control.ttcs',
        emailPersonal,
        status,
        expiraEn: sendEmail
          ? new Date(Date.now() + 24 * 3600 * 1000).toISOString()
          : null,
        success: true,
        mensaje: sendEmail
          ? `Invitación enviada a ${emailPersonal}`
          : 'Usuario creado. Puedes enviar invitación después.',
      }),
    });
  });
  return { calls: () => calls };
}
