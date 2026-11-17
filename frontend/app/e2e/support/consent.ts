/**
 * Sembrado del consentimiento y de la aceptacion de terminos.
 *
 * <p>La app levanta dos capas bloqueantes antes de dejar trabajar:
 *
 * <ul>
 *   <li>El banner de cookies, que decide con `localStorage['cookie_consent']`.
 *   <li>El modal de terminos, que decide con `sessionStorage['ce.terms.accepted']`
 *       y con la version que declara el servidor.
 * </ul>
 *
 * <p>Las dos se montan por encima del contenido: el banner intercepta los
 * clics (`cookie-banner subtree intercepts pointer events`) y el guard
 * {@code authGuard} manda a {@code /terminos} a quien no aceptan, de modo que
 * la navegacion nunca llega a la pantalla que el test queria probar.
 *
 * <p>El banner se siembra para TODOS los tests, porque a nadie le importa si
 * el consentimiento de cookies esta dado: es una pantalla de la aplicacion,
 * no una regla de negocio.
 *
 * <p>Los terminos NO se siembran globalmente, y es deliberado: el spec J6
 * prueba justamente el caso de "el usuario todavia no acepto" y espera que el
 * modal aparezca. Sembrar la aceptacion lo haria fallar. Quien necesite
 * saltarse el modal usa {@code scriptTerminosAceptados}, y quien quiera
 * aceptarlo de verdad (contra la API real) pulsa el boton del modal.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';

/** Clave de `localStorage` donde vive el consentimiento de cookies. */
export const CLAVE_CONSENTIMIENTO_COOKIES = 'cookie_consent';

/**
 * Clave de `sessionStorage` donde la app recuerda la version de terminos que
 * el usuario acepto en esta sesion de navegador.
 *
 * <p>La usa {@code TermsAcceptanceService.aceptadaEnEstaSesion()}: si coincide
 * con la version vigente, el modal no aparece.
 */
export const CLAVE_TERMINOS_ACEPTADOS = 'ce.terms.accepted';

/** Version por defecto, la misma que declara el backend (`legal.terms-version`). */
export const VERSION_TERMINOS_POR_DEFECTO = '1.0';

/** Estado de las preferencias de cookies con el consentimiento ya dado. */
export function preferenciasCookies(): Record<string, boolean> {
  return {
    essential: true,
    analytics: false,
    marketing: false,
    preferences: false,
  };
}

/**
 * Estado de consentimiento que la app espera en `localStorage`.
 *
 * <p>La forma es la de {@code CookieConsentService}: version, timestamp,
 * `consent_given` y las preferencias.
 */
export function consentimientoCookies(): string {
  return JSON.stringify({
    version: 1,
    timestamp: Date.now(),
    consent_given: true,
    preferences: preferenciasCookies(),
  });
}

/**
 * Lee el estado que dejo el global setup.
 *
 * @returns el estado del backend, o `null` si el setup todavia no corrio
 */
export function leerEstadoBackend(): {
  online: boolean;
  baseUrl: string;
  legalVersion: string;
} | null {
  const archivo = path.join(__dirname, '.generated', 'backend.json');
  try {
    if (!fs.existsSync(archivo)) {
      return null;
    }
    return JSON.parse(fs.readFileSync(archivo, 'utf-8'));
  } catch {
    return null;
  }
}

/**
 * Version vigente de los terminos segun el backend.
 *
 * <p>Si el global setup no pudo leerla, se usa la por defecto: es la misma que
 * declara {@code legal.terms-version} en el backend, y el fixture tiene un
 * respaldo que acepta el modal si, aun asi, aparece.
 */
export function versionTerminosVigente(): string {
  return leerEstadoBackend()?.legalVersion ?? VERSION_TERMINOS_POR_DEFECTO;
}

/**
 * Script que siembra el consentimiento de cookies.
 *
 * <p>Se inyecta como contenido, no como funcion, porque el contexto lo evalua
 * en el navegador: no puede cerrar sobre valores de Node.
 *
 * @returns el codigo JavaScript que se ejecuta antes de cada documento
 */
export function scriptConsentimientoCookies(): string {
  return `try {
    localStorage.setItem(${JSON.stringify(CLAVE_CONSENTIMIENTO_COOKIES)}, ${JSON.stringify(consentimientoCookies())});
  } catch (error) {
    // Modo privado de algunos navegadores: sin almacenamiento no hay banner.
  }`;
}

/**
 * Estado de consentimiento completo, listo para escribir en `localStorage`.
 *
 * <p>Es la via que usa el global setup: escribe el valor en la pagina y
 * guarda el `storageState`, que el config aplica a cada contexto.
 */
export function escribirConsentimientoCookies(): string {
  return consentimientoCookies();
}

/**
 * Script que da por aceptada una version de terminos.
 *
 * <p>Es para uso puntual de un spec (por ejemplo, un flujo que ya acepta los
 * terminos antes de empezar). No se inyecta globalmente: J6 necesita que el
 * modal aparezca.
 *
 * @param version version de terminos que se da por aceptada
 * @returns el codigo JavaScript que se ejecuta antes de cada documento
 */
export function scriptTerminosAceptados(version: string): string {
  return `try {
    sessionStorage.setItem(${JSON.stringify(CLAVE_TERMINOS_ACEPTADOS)}, ${JSON.stringify(version)});
  } catch (error) {
    // Modo privado: el modal aparecera y habra que aceptarlo a mano.
  }`;
}
