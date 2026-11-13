/**
 * Preparacion global de la suite E2E.
 *
 * <p>Se ejecuta una vez, antes de que corra el primer test, y deja el entorno
 * en un estado conocido:
 *
 * <ul>
 *   <li>Comprueba si el backend de la API esta en pie y, si lo esta, lee la
 *       version vigente de los terminos. Esa version es la que decide si el
 *       modal de aceptacion bloquea el flujo: si el E2E siembra una version
 *       distinta de la del servidor, el modal aparece y rompe los tests que
 *       navegan a una ruta protegida.
 *   <li>Escribe el `storageState` con el consentimiento de cookies ya dado.
 *       El banner de cookies se monta por encima del contenido e intercepta
 *       los clics (`cookie-banner subtree intercepts pointer events`): sin
 *       esto, ninguna pantalla alcanza a renderizar.
 *   <li>Escribe el resultado del sondeo en {@code e2e/.generated/backend.json},
 *       que es lo que leen el config de Playwright y los fixtures.
 * </ul>
 *
 * <p>No aborta la corrida cuando el backend no responde: los tests que
 * interceptan la red con mocks no lo necesitan. Los que si lo necesitan
 * (la ruta de registro de acta) fallan con un mensaje explicito, no con un
 * error de red. Para exigir el backend hay que correr con
 * {@code E2E_REQUIRE_BACKEND=1}.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';

import { chromium, type FullConfig } from '@playwright/test';

import { escribirConsentimientoCookies } from './support/consent';

/** Base de la API, tal como la ve el navegador a traves del proxy de Vite. */
const API_BASE = process.env['E2E_API_URL'] ?? 'http://localhost:8080';

/** Version por defecto, la misma que declara el backend (`legal.terms-version`). */
export const VERSION_TERMINOS_POR_DEFECTO = '1.0';

/** Carpeta donde se deja lo que el setup descubre. */
export const CARPETA_GENERADA = path.join(__dirname, '.generated');

/** Archivo con lo que el setup descubrió. */
export const ARCHIVO_ESTADO_BACKEND = path.join(
  CARPETA_GENERADA,
  'backend.json',
);

/** Archivo con el `storageState` que aplica el config a cada contexto. */
export const ARCHIVO_STORAGE_STATE = path.join(
  CARPETA_GENERADA,
  'storage-state.json',
);

/** Forma del archivo que el setup escribe. */
export interface EstadoBackend {
  /** Si el backend respondio al sondeo. */
  online: boolean;
  /** Base de la API sondeada. */
  baseUrl: string;
  /** Version vigente de los terminos, o la por defecto si no se pudo leer. */
  legalVersion: string;
}

/**
 * Sondea el backend y devuelve la version vigente de los terminos.
 *
 * @returns la version leida, o `null` si el backend no responde
 */
async function leerVersionTerminos(): Promise<string | null> {
  try {
    const control = new AbortController();
    const temporizador = setTimeout(() => control.abort(), 5000);
    const respuesta = await fetch(`${API_BASE}/api/v1/legal/current-version`, {
      signal: control.signal,
    });
    clearTimeout(temporizador);

    if (!respuesta.ok) {
      return null;
    }

    const cuerpo = (await respuesta.json()) as { version?: string };
    return cuerpo.version ?? null;
  } catch {
    return null;
  }
}

/**
 * Escribe el `storageState` con el consentimiento de cookies ya dado.
 *
 * <p>El estado se arma Navegando a la app en un contexto propio: el
 * `localStorage` se escribe en el origen real de la app, que es lo que el
 * `storageState` guarda y lo que el config aplica despues a cada contexto.
 *
 * @param baseUrl origen de la app, tal como lo usa el config
 */
async function escribirStorageState(baseUrl: string): Promise<void> {
  const navegador = await chromium.launch();
  try {
    const contexto = await navegador.newContext();
    const pagina = await contexto.newPage();
    await pagina.goto(baseUrl, { waitUntil: 'domcontentloaded' });
    await pagina.evaluate((consentimiento) => {
      localStorage.setItem('cookie_consent', consentimiento);
    }, escribirConsentimientoCookies());
    await contexto.storageState({ path: ARCHIVO_STORAGE_STATE });
  } finally {
    await navegador.close();
  }
}

/**
 * Prepara el entorno E2E.
 *
 * @param config config de Playwright; de ahi sale el origen de la app.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  fs.mkdirSync(CARPETA_GENERADA, { recursive: true });

  const baseUrl =
    config.projects[0]?.use?.baseURL?.toString() ?? 'http://localhost:4200';

  try {
    await escribirStorageState(baseUrl);
  } catch (error) {
    // Sin la app en pie no hay storageState: el banner aparecera y los tests
    // fallaran con el error del overlay, que es menos claro que este aviso.
    console.warn(
      `[e2e] No se pudo preparar el storageState en ${baseUrl}: ${String(error)}`,
    );
  }

  const version = await leerVersionTerminos();
  const online = version !== null;

  const estado: EstadoBackend = {
    online,
    baseUrl: API_BASE,
    legalVersion: version ?? VERSION_TERMINOS_POR_DEFECTO,
  };

  fs.writeFileSync(ARCHIVO_ESTADO_BACKEND, `${JSON.stringify(estado, null, 2)}\n`);

  if (online) {
    console.log(
      `[e2e] Backend en pie en ${API_BASE} — version de terminos: ${estado.legalVersion}`,
    );
    return;
  }

  if (process.env['E2E_REQUIRE_BACKEND'] === '1') {
    throw new Error(
      `[e2e] No hay backend en ${API_BASE} y E2E_REQUIRE_BACKEND=1. ` +
        'Levantá el stack (docker compose --profile dev up -d) antes de correr.',
    );
  }

  console.warn(
    `[e2e] Sin backend en ${API_BASE}: los tests que interceptan la red siguen, ` +
      'los que hablan con la API real no pueden pasar.',
  );
}
