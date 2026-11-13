/**
 * Global setup de la suite E2E.
 *
 * <p>Se ejecuta una vez, antes de que corra el primer test, y deja el entorno
 * en un estado conocido:
 *
 * <ul>
 *   <li>Detecta y acepta el banner de cookies. El banner se monta por encima
 *       del contenido e intercepta los clics (`cookie-banner subtree
 *       intercepts pointer events`): sin esto, ninguna pantalla alcanza a
 *       renderizar.
 *   <li>Entra con un usuario real del seed y acepta los terminos si el modal
 *       aparece. El guard {@code authGuard} manda a {@code /terminos} a quien
 *       no acepta, de modo que la navegacion nunca llega a la pantalla que el
 *       test queria probar.
 *   <li>Guarda la sesion en {@code auth.json}. El config lo aplica como
 *       {@code storageState} a cada contexto: los tests empiezan con el
 *       consentimiento dado y con la cookie de refresh.
 *   <li>Sondea el backend y deja el resultado en
 *       {@code e2e/.generated/backend.json}, que leen el config y los fixtures.
 * </ul>
 *
 * <p>No aborta la corrida cuando el backend no responde: los tests que
 * interceptan la red con mocks no lo necesitan. Los que si lo necesitan (la
 * ruta de registro de acta) fallan con un mensaje explicito. Para exigir el
 * backend hay que correr con {@code E2E_REQUIRE_BACKEND=1}.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';

import { chromium, type FullConfig } from '@playwright/test';

import { escribirConsentimientoCookies } from './e2e/support/consent';
import { USUARIOS_SEED } from './e2e/support/usuarios-seed';

/** Base de la API, tal como la ve el navegador a traves del proxy de Vite. */
const API_BASE = process.env['E2E_API_URL'] ?? 'http://localhost:8080';

/** Version por defecto, la misma que declara el backend (`legal.terms-version`). */
export const VERSION_TERMINOS_POR_DEFECTO = '1.0';

/** Sesion guardada; el config la aplica como `storageState`. */
export const ARCHIVO_AUTH = path.join(__dirname, 'auth.json');

/** Carpeta donde se deja lo que el setup descubre. */
export const CARPETA_GENERADA = path.join(__dirname, 'e2e', '.generated');

/** Archivo con el resultado del sondeo del backend. */
export const ARCHIVO_ESTADO_BACKEND = path.join(
  CARPETA_GENERADA,
  'backend.json',
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
 * Acepta el banner de cookies si esta en pantalla.
 *
 * @param pagina pagina con la que trabajar
 */
async function aceptarBannerCookies(pagina: import('@playwright/test').Page) {
  const boton = pagina.getByRole('button', { name: /aceptar todo/i }).first();
  try {
    await boton.waitFor({ state: 'visible', timeout: 5_000 });
  } catch {
    return;
  }
  await boton.click();
}

/**
 * Acepta el modal de terminos si esta en pantalla.
 *
 * @param pagina pagina con la que trabajar
 */
async function aceptarModalTerminos(pagina: import('@playwright/test').Page) {
  const boton = pagina.getByTestId('terms-accept');
  try {
    await boton.waitFor({ state: 'visible', timeout: 5_000 });
  } catch {
    return;
  }
  await boton.click();
  await pagina
    .waitForURL((url) => !url.pathname.includes('/terminos'), { timeout: 15_000 })
    .catch(() => undefined);
}

/**
 * Entra con un usuario real del seed y acepta los terminos.
 *
 * @param pagina pagina con la que trabajar
 */
async function iniciarSesionReal(pagina: import('@playwright/test').Page) {
  const usuario = USUARIOS_SEED.OBSERVER;

  await pagina.goto('/login');
  await pagina.locator('#email').fill(usuario.email);
  await pagina.locator('#password').fill(usuario.password);
  await pagina
    .getByRole('button', { name: /ingresar|login|entrar/i })
    .click();

  await aceptarModalTerminos(pagina);
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

  const version = await leerVersionTerminos();
  const online = version !== null;

  const estado: EstadoBackend = {
    online,
    baseUrl: API_BASE,
    legalVersion: version ?? VERSION_TERMINOS_POR_DEFECTO,
  };
  fs.writeFileSync(
    ARCHIVO_ESTADO_BACKEND,
    `${JSON.stringify(estado, null, 2)}\n`,
  );

  const navegador = await chromium.launch();
  try {
    const contexto = await navegador.newContext();
    const pagina = await contexto.newPage();

    // El consentimiento se sembla antes de que arranque la app: el banner se
    // decide al inicializar, y ahi no hay forma de sembrarlo.
    await pagina.addInitScript((consentimiento) => {
      try {
        localStorage.setItem('cookie_consent', consentimiento);
      } catch {
        // Modo privado de algunos navegadores.
      }
    }, escribirConsentimientoCookies());

    try {
      await pagina.goto(baseUrl, { waitUntil: 'domcontentloaded' });
      await aceptarBannerCookies(pagina);
    } catch (error) {
      console.warn(
        `[e2e] No se pudo abrir ${baseUrl} para aceptar el banner: ${String(error)}`,
      );
    }

    // La sesion real solo se puede armar con el backend en pie. Sin el, los
    // tests con mocks siguen funcionando: ellos llevan su propia sesion.
    if (online) {
      try {
        await iniciarSesionReal(pagina);
      } catch (error) {
        console.warn(
          `[e2e] No se pudo iniciar sesion real: ${String(error)}`,
        );
      }
    }

    await contexto.storageState({ path: ARCHIVO_AUTH });
  } finally {
    await navegador.close();
  }

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
