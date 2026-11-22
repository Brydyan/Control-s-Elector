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
    // Solo la lista: el POST de crear usuario lo intercepta `mockCrearUsuario`.
    if (route.request().method() !== 'GET') {
      await route.fallback();
      return;
    }
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
 * Creacion de una persona con invitacion (ODD #504).
 *
 * <p>El flujo ya no tiene un boton "Enviar invitacion" aparte: el modal de
 * usuario tiene un unico boton, "Guardar", que crea la cuenta y decide si
 * manda la invitacion por email segun el toggle. Por eso la mock intercepta
 * `POST /admin/usuarios` y mira `sendInvitation` en el cuerpo.
 *
 * @returns el numero de llamadas y el ultimo cuerpo enviado
 */
export async function mockCrearUsuario(
  page: Page,
  opciones?: {
    publicId?: string;
    username?: string;
  },
): Promise<{ calls: () => number; body: () => Record<string, unknown> | null }> {
  let calls = 0;
  let ultimo: Record<string, unknown> | null = null;
  await page.route(`${API}/admin/usuarios`, async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    calls += 1;
    const cuerpo = (route.request().postDataJSON() ?? {}) as Record<
      string,
      unknown
    >;
    ultimo = cuerpo;
    const sendInvitation = cuerpo['sendInvitation'] !== false;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        publicId: opciones?.publicId ?? 'e2e-invitado-uuid',
        email: opciones?.username ?? 'juagarobs@control.ttcs',
        nombre: cuerpo['nombre'] ?? 'Juan',
        apellido: cuerpo['apellido'] ?? 'García',
        rolNombre: cuerpo['rolNombre'] ?? 'OBSERVER',
        habilitado: true,
        creadoEn: '2026-11-22T00:00:00Z',
        estadoUsuario: sendInvitation ? 'PENDIENTE_INVITACION' : 'ACTIVO',
        passwordTemporal: null,
      }),
    });
  });
  return { calls: () => calls, body: () => ultimo };
}
