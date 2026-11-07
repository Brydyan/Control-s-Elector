/**
 * Load Test: Invitaciones de usuario por email
 * T-AUTH-INV-01 — Task 4.4
 *
 * Purpose: Measure the invitation path under load
 * - POST /auth/invitaciones  (MASTER sending batches of invitations)
 * - GET  /auth/invitaciones/validate-token/{token} + POST /auth/activate
 *
 * Two legs, measured apart:
 * - `invite`  : the MASTER sends invitation batches. Repeatable — inviting a pending
 *               account again just re-sends the link, the account stays PENDIENTE.
 * - `activate`: validates and consumes real activation tokens.
 *
 * The activation leg needs real tokens: the token travels in the email and the email
 * leaves through the queue, so the script cannot mint one. They are passed in with
 * INVITATION_TOKENS (comma separated). Without tokens the script measures only the
 * send path and says so — it does not invent numbers for the leg it did not run.
 *
 * Run: k6 run scenarios/invitations.js
 *      INVITATION_TOKENS=t1,t2 k6 run scenarios/invitations.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { config, apiUrl } from '../config.js';
import { login, authHeaders } from '../common.js';

/** Tokens de activacion reales, separados por coma. Sin ellos no corre la pata `activate`. */
const TOKENS = (__ENV.INVITATION_TOKENS || '')
  .split(',')
  .map((token) => token.trim())
  .filter((token) => token.length > 0);

/** Cuantas cuentas se incluyen en cada lote de invitacion. */
const LOTE = parseInt(__ENV.INVITE_BATCH || '10', 10);

/** VUs concurrentes de cada pata. */
const VUS_INVITE = parseInt(__ENV.INVITE_VUS || '10', 10);
const VUS_ACTIVATE = parseInt(__ENV.ACTIVATE_VUS || '10', 10);

/** Duracion del cuerpo de la prueba. */
const DURACION = __ENV.INVITE_DURATION || '5m';

/** Contrasena que se usa al activar. Cumple los requisitos de contrasena del backend. */
const PASSWORD_ACTIVACION =
  __ENV.ACTIVATION_PASSWORD || 'Observer@2025!';

const thresholds = {
  'http_req_duration{endpoint:invite}': ['p(95)<5000', 'p(99)<8000'],
  'http_req_failed{endpoint:invite}': ['rate<0.1'],
};

if (TOKENS.length > 0) {
  thresholds['http_req_duration{endpoint:activate}'] = ['p(95)<2000', 'p(99)<3000'];
  thresholds['http_req_failed{endpoint:activate}'] = ['rate<0.1'];
}

export const options = {
  scenarios: {
    invite: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: VUS_INVITE },
        { duration: DURACION, target: VUS_INVITE },
        { duration: '30s', target: 0 },
      ],
      exec: 'invite',
      tags: { endpoint: 'invite' },
    },
    ...(TOKENS.length > 0
      ? {
          activate: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
              { duration: '30s', target: VUS_ACTIVATE },
              { duration: DURACION, target: VUS_ACTIVATE },
              { duration: '30s', target: 0 },
            ],
            exec: 'activate',
            tags: { endpoint: 'activate' },
          },
        }
      : {}),
  },
  thresholds,
  ext: {
    loadimpact: {
      name: 'T-AUTH-INV-01: Invitaciones por email',
      projectID: 3356,
    },
  },
};

/**
 * Lista las cuentas que siguen esperando la activacion.
 *
 * @param {string} token - JWT del MASTER
 * @returns {array} Cuentas PENDIENTE_INVITACION
 */
function listarPendientes(token) {
  const response = http.get(apiUrl('/admin/usuarios?page=0&size=100'), {
    headers: authHeaders(token),
    tags: { endpoint: 'invite' },
  });

  check(response, {
    'listado de usuarios status 200': (r) => r.status === 200,
  });

  const usuarios = response.json('data');
  if (!Array.isArray(usuarios)) {
    return [];
  }
  return usuarios.filter(
    (usuario) => usuario.estadoUsuario === 'PENDIENTE_INVITACION',
  );
}

/** Pata de envio: el MASTER invita a un lote de cuentas pendientes. */
export function invite() {
  const token = login(config.master, { scenario: 'invitations' });
  const pendientes = listarPendientes(token);

  if (pendientes.length === 0) {
    // Sin cuentas pendientes no hay nada que medir: avisar en vez de inventar el numero.
    console.warn(
      'No hay cuentas PENDIENTE_INVITACION en la base: la pata de envio no midio nada.',
    );
    sleep(5);
    return;
  }

  const lote = pendientes.slice(0, LOTE).map((usuario) => usuario.publicId);
  const response = http.post(
    apiUrl('/auth/invitaciones'),
    JSON.stringify({ usuarioIds: lote }),
    {
      headers: authHeaders(token),
      tags: { endpoint: 'invite' },
    },
  );

  check(response, {
    'invitaciones status 202': (r) => r.status === 202,
    'invitaciones con contador': (r) => r.json('sentCount') !== undefined,
    'invitaciones en menos de 5s': (r) => r.timings.duration < 5000,
  });

  sleep(parseInt(__ENV.SLEEP_DURATION || '2', 10));
}

/** Pata de activacion: valida y consume tokens de activacion reales. */
export function activate() {
  if (TOKENS.length === 0) {
    return;
  }

  const token = TOKENS[(__VU - 1) % TOKENS.length];

  const validacion = http.get(
    apiUrl(`/auth/invitaciones/validate-token/${encodeURIComponent(token)}`),
    { tags: { endpoint: 'activate' } },
  );

  check(validacion, {
    'validate-token status 200': (r) => r.status === 200,
    'validate-token dice que sirve': (r) => r.json('valid') === true,
  });

  const activacion = http.post(
    apiUrl('/auth/activate'),
    JSON.stringify({
      token,
      password: PASSWORD_ACTIVACION,
      passwordConfirm: PASSWORD_ACTIVACION,
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'activate' },
    },
  );

  check(activacion, {
    // El token es de un solo uso: el 409 es la respuesta correcta cuando otra
    // iteracion ya lo consumio, no un fallo del sistema.
    'activate responde 200 o 409': (r) => r.status === 200 || r.status === 409,
    'activate en menos de 2s': (r) => r.timings.duration < 2000,
  });

  sleep(1);
}

export function setup() {
  if (TOKENS.length === 0) {
    console.warn(
      'INVITATION_TOKENS no viene definido: se mide solo el envio de invitaciones, no la activacion.',
    );
  }
  return { tokens: TOKENS.length };
}
