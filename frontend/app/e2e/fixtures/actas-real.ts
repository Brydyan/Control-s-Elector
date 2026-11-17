import { expect } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';

/**
 * Utilidades para los journeys que hablan con la API real.
 *
 * <p>Preparar el estado electoral (registrar un acta, devolverla a ANULADA)
 * se hace por HTTP: lo que se prueba con el navegador es el flujo de la
 * pantalla, no el alta del acta. El alta por UI ya la cubre J1.
 */

/** Padrón de la JRV usada en los journeys: chico, para que el cuadre sea obvio. */
export const SUFRAGANTES = 10;
export const BLANCOS = 1;
export const NULOS = 1;

/**
 * Devuelve el access token de un usuario del seed.
 *
 * @param request cliente HTTP
 * @param email correo del usuario
 * @param password password del usuario
 * @returns el access token
 */
export async function tokenDe(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<string> {
  const login = await request.post('/api/v1/auth/login', {
    data: { email, password },
  });
  expect(login.ok()).toBeTruthy();
  const cuerpo = (await login.json()) as { accessToken: string };
  return cuerpo.accessToken;
}

/**
 * Busca el primer par (JRV, cargo) sin acta activa para un observer.
 *
 * @param request cliente HTTP
 * @param token access token del observer
 * @returns el par libre
 */
export async function comboLibre(
  request: APIRequestContext,
  token: string,
): Promise<{
  jrvId: number;
  cargoId: number;
}> {
  const cabeceras = { Authorization: `Bearer ${token}` };

  const jrvs = (await (
    await request.get('/api/v1/observer/jrvs', { headers: cabeceras })
  ).json()) as Array<{ id: number; cargosPendientes: number }>;

  for (const jrv of jrvs) {
    if (jrv.cargosPendientes <= 0) {
      continue;
    }
    const cargos = (await (
      await request.get(`/api/v1/observer/jrv/${jrv.id}/cargos`, {
        headers: cabeceras,
      })
    ).json()) as Array<{ id: number; registrado: boolean }>;

    for (const cargo of cargos) {
      if (!cargo.registrado) {
        return { jrvId: jrv.id, cargoId: cargo.id };
      }
    }
  }

  throw new Error(
    'La base no tiene ningún par (JRV, cargo) libre: las actas de la corrida ' +
      'anterior no se devolvieron a ANULADA.',
  );
}

/**
 * Registra un acta ENVIADA por HTTP.
 *
 * @param request cliente HTTP
 * @param token access token del observer
 * @returns el id del acta creada
 */
export async function registrarActa(
  request: APIRequestContext,
  token: string,
): Promise<number> {
  const cabeceras = { Authorization: `Bearer ${token}` };
  const combo = await comboLibre(request, token);

  const candidatos = (await (
    await request.get(
      `/api/v1/configuracion/candidatos?cargoId=${combo.cargoId}`,
      { headers: cabeceras },
    )
  ).json()) as Array<{ id: number; nombre: string; partidoId: number }>;

  const votosDelPrimero = SUFRAGANTES - BLANCOS - NULOS;

  const respuesta = await request.post('/api/v1/observer/actas', {
    headers: { ...cabeceras, 'Content-Type': 'application/json' },
    data: {
      jrvId: combo.jrvId,
      cargoId: combo.cargoId,
      totalSufragantes: SUFRAGANTES,
      votosBlancos: BLANCOS,
      votosNulos: NULOS,
      detalles: candidatos.map((candidato, indice) => ({
        candidatoId: candidato.id,
        candidatoNombre: candidato.nombre,
        votos: indice === 0 ? votosDelPrimero : 0,
      })),
    },
  });

  expect(respuesta.status()).toBe(201);
  const acta = (await respuesta.json()) as { id: number };
  return acta.id;
}

/**
 * Aprueba un acta como coordinator.
 *
 * @param request cliente HTTP
 * @param actaId id del acta
 */
export async function aprobar(
  request: APIRequestContext,
  actaId: number,
): Promise<void> {
  const token = await tokenDe(
    request,
    'coordinatorcortes@example.local',
    'admin12345',
  );
  const respuesta = await request.put(
    `/api/v1/coordinator/actas/${actaId}/aprobar`,
    {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: {},
    },
  );
  expect(respuesta.status()).toBe(200);
}

/**
 * Devuelve un acta a ANULADA para liberar su par.
 *
 * <p>La anulacion solo existe para las actas APROBADAS: un acta ENVIADA pasa
 * antes por la aprobacion del coordinator.
 *
 * @param request cliente HTTP
 * @param actaId id del acta
 */
export async function anular(
  request: APIRequestContext,
  actaId: number,
): Promise<void> {
  const token = await tokenDe(
    request,
    'admin@control-elector.local',
    'admin12345',
  );
  await request.delete(`/api/v1/coordinator/actas/${actaId}`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { motivo: 'Limpieza de la suite E2E' },
  });
}

/**
 * Estado de un acta, leido de la API.
 *
 * @param request cliente HTTP
 * @param token access token del observer
 * @param actaId id del acta
 * @returns el estado del acta
 */
export async function estadoDe(
  request: APIRequestContext,
  token: string,
  actaId: number,
): Promise<string> {
  const respuesta = await request.get(`/api/v1/observer/actas/${actaId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const acta = (await respuesta.json()) as { estado: string };
  return acta.estado;
}

/**
 * Espera a que la pantalla de detalle de un acta esté lista.
 *
 * @param page pagina con la que trabajar
 */
export async function esperarDetalle(page: Page): Promise<void> {
  await expect(page.locator('h1')).toBeVisible({ timeout: 15_000 });
}
