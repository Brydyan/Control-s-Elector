import { test, expect } from './fixtures/auth.fixture';

/**
 * Caché de datos estáticos — E2E (T-CACHE-STRAT-01 F.3 / F.4).
 *
 * Qué prueba:
 *  - F.3: el formulario del observador carga candidatos de 5 cargos y solo
 *    pide `/configuracion/partidos` UNA vez (caché `shareReplay(1)` en
 *    `ObserverApi.getCandidatosPorCargo`), no una por cargo.
 *  - F.4: el dashboard carga sus 4 endpoints una vez y al saltar entre
 *    Dashboard / Resultados / Progreso NO vuelve a pedir nada: la caché vive
 *    en el servicio raíz (`DashboardApi`), no en el componente.
 *
 * Cómo cuenta: un listener de `request` cuenta por URL. Las navegaciones son
 * cliente (routerLink), NO `page.goto`: un `goto` recarga la app entera y
 * mataría el caché en memoria, que es justo lo que el test quiere observar.
 */

/** Contadores de peticiones por recurso. */
type Contadores = Record<string, number>;

function contarPorUrl(
  page: import('@playwright/test').Page,
  claves: string[],
): Contadores {
  const contadores: Contadores = Object.fromEntries(
    claves.map((clave) => [clave, 0]),
  );
  page.on('request', (req) => {
    const url = req.url();
    for (const clave of claves) {
      if (url.includes(clave)) {
        contadores[clave] += 1;
      }
    }
  });
  return contadores;
}

const PARTIDOS = [
  { id: 1, nombre: 'Movimiento Ciudadano', siglas: 'MC', numeroLista: 5 },
  { id: 2, nombre: 'Alianza País', siglas: 'AP', numeroLista: 7 },
];

/**
 * Navega por el menú lateral abriendo el panel si el layout es el de móvil.
 *
 * <p>En móvil la barra lateral es un panel fuera de pantalla (`transform:
 * translateX(-100%)`): el enlace existe en el DOM pero no es pulsable hasta
 * que se abre con el botón del topbar. La navegación del router es de
 * cliente en ambos casos, que es lo que conserva la caché en memoria.
 *
 * @param page pagina de Playwright
 * @param nombre texto visible del enlace del menú
 */
async function irPorMenu(
  page: import('@playwright/test').Page,
  nombre: RegExp,
) {
  const link = page.getByRole('link', { name: nombre }).first();

  // En móvil la barra lateral vive fuera de pantalla: se abre con el botón
  // del topbar si su caja empieza fuera del viewport.
  const caja = await page.locator('aside.layout-sidebar').boundingBox();
  if (!caja || caja.x < 0) {
    await page
      .getByRole('button', { name: /menú lateral/i })
      .first()
      .click();
    await expect
      .poll(
        async () => {
          const b = await page.locator('aside.layout-sidebar').boundingBox();
          return b ? b.x : -1;
        },
        { timeout: 10_000 },
      )
      .toBeGreaterThanOrEqual(0);
  }

  // Con la barra lateral abierta, el velo (`layout-mask`) tapa la página: el
  // clic real lo intercepta. Se dispara el evento del enlace directamente.
  await link.evaluate((el) => (el as HTMLAnchorElement).click());
}

/** Cinco cargos del mismo JRV, como el seed de la app. */
const CARGOS = [10, 11, 12, 13, 14].map((id) => ({
  cargoId: id,
  nombre: `Cargo ${id}`,
  nivel: 'MUNICIPAL',
  registrado: false,
}));

test.describe('Caché de datos estáticos (T-CACHE-STRAT-01)', () => {
  // -------------------------------------------------------------------------
  // F.3 — Formulario del observador: los partidos se piden una sola vez
  // -------------------------------------------------------------------------

  test('F.3 — 5 cargos: 1 petición de partidos y 5 de candidatos', async ({
    observerPage: page,
  }) => {
    await page.route('**/api/v1/observer/jrvs**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            jrvId: 1,
            numero: 'JRV-001',
            parroquia: 'La Libertad',
            cargosPendientes: 5,
            cargosRegistrados: 0,
          },
        ]),
      });
    });

    await page.route('**/api/v1/observer/jrv/*/cargos**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(CARGOS),
      });
    });

    await page.route('**/api/v1/configuracion/partidos**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(PARTIDOS),
      });
    });

    await page.route('**/api/v1/configuracion/candidatos**', async (route) => {
      const cargoId = new URL(route.request().url()).searchParams.get(
        'cargoId',
      );
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: Number(cargoId) * 100,
            nombre: 'Candidato Uno',
            orden: 1,
            cargoId: Number(cargoId),
            partidoId: 1,
          },
          {
            id: Number(cargoId) * 100 + 1,
            nombre: 'Candidato Dos',
            orden: 2,
            cargoId: Number(cargoId),
            partidoId: 2,
          },
        ]),
      });
    });

    const contadores = contarPorUrl(page, [
      '/configuracion/partidos',
      '/configuracion/candidatos',
    ]);

    await page.goto('/observer/jrv/1/cargos', {
      waitUntil: 'networkidle',
      timeout: 15_000,
    });

    // Un clic por cargo: la navegación es de cliente, así que el singleton
    // `ObserverApi` sobrevive y la caché de partidos se conserva.
    for (const [indice, cargo] of CARGOS.entries()) {
      await page
        .getByRole('button', { name: /registrar acta|ver \/ modificar/i })
        .nth(indice)
        .click();
      await expect(page.getByTestId('registrar-acta')).toBeVisible({
        timeout: 10_000,
      });
      // Volver a la lista de cargos (navegación de cliente).
      await page.goBack();
      await expect(
        page.getByRole('button', { name: /registrar acta|ver \/ modificar/i }),
      ).toHaveCount(CARGOS.length, { timeout: 10_000 });
    }

    // Partidos: una sola vez para los 5 cargos (caché `shareReplay(1)`).
    expect(contadores['/configuracion/partidos']).toBe(1);
    // Candidatos: uno por cargo, porque la lista de candidatos es por cargo.
    expect(contadores['/configuracion/candidatos']).toBe(CARGOS.length);
  });

  // -------------------------------------------------------------------------
  // F.4 — Dashboard: los 4 endpoints se piden una vez, no una por pestaña
  // -------------------------------------------------------------------------

  test('F.4 — Dashboard: 4 peticiones iniciales y 0 al cambiar de sección', async ({
    masterPage: page,
  }) => {
    await page.route('**/api/v1/dashboard/resumen**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          enviadas: 10,
          enDisputa: 1,
          corregidas: 2,
          aprobadas: 7,
          totalActas: 20,
        }),
      });
    });

    await page.route('**/api/v1/dashboard/resultados**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            cargoId: 10,
            cargoNombre: 'Alcalde',
            votosBlancos: 5,
            votosNulos: 3,
            candidatos: [{ id: 1, nombre: 'Carlos', votos: 100, partidoId: 1 }],
          },
        ]),
      });
    });

    await page.route('**/api/v1/dashboard/progreso**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            cantonId: 1,
            nombre: 'Los Ríos',
            totalJrvs: 10,
            jrvsConActa: 8,
            porcentaje: 80,
            parroquias: [],
          },
        ]),
      });
    });

    await page.route('**/api/v1/dashboard/novedades**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    const contadores = contarPorUrl(page, [
      '/dashboard/resumen',
      '/dashboard/resultados',
      '/dashboard/progreso',
      '/dashboard/novedades',
    ]);

    await page.goto('/dashboard', {
      waitUntil: 'networkidle',
      timeout: 15_000,
    });

    expect(contadores['/dashboard/resumen']).toBe(1);
    expect(contadores['/dashboard/resultados']).toBe(1);
    expect(contadores['/dashboard/progreso']).toBe(1);
    expect(contadores['/dashboard/novedades']).toBe(1);

    // Cambiar de sección por el menú lateral (routerLink → navegación de cliente).
    await irPorMenu(page, /resultados/i);
    await expect(page.getByTestId('resultados-cargo')).toBeVisible({
      timeout: 10_000,
    });

    await irPorMenu(page, /progreso/i);
    await expect(page.getByTestId('progreso-cantones')).toBeVisible({
      timeout: 10_000,
    });

    await irPorMenu(page, /^dashboard$/i);
    await expect(page.getByTestId('dashboard-error')).toHaveCount(0, {
      timeout: 10_000,
    });

    // La caché de `DashboardApi` (servicio raíz) evitó toda segunda vuelta.
    expect(contadores['/dashboard/resumen']).toBe(1);
    expect(contadores['/dashboard/resultados']).toBe(1);
    expect(contadores['/dashboard/progreso']).toBe(1);
    expect(contadores['/dashboard/novedades']).toBe(1);
  });
});
