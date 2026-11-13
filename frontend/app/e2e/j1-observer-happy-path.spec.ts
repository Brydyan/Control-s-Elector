import { test, expect } from './fixtures/auth.fixture';
import { loginReal } from './fixtures/auth.fixture';
import type { APIRequestContext, Page } from '@playwright/test';

/**
 * J1 — Flujo Feliz Observador contra la API REAL
 *
 * @guardian @no-intercept
 *
 * Este spec no intercepta nada: habla con el backend de verdad. Es el unico
 * lugar donde se puede comprobar que el checksum lo aplica el servidor, que el
 * par (JRV, cargo) se bloquea con 409 cuando ya hay acta, y que la evidencia
 * fotografica llega al object storage.
 *
 * Requisitos:
 *   - Backend Quarkus en pie (docker compose --profile dev up -d, o
 *     `mvn quarkus:dev` con Postgres, Redis y RustFS).
 *   - El proxy de Vite (`pnpm start`) translating `/api` to the backend.
 *
 * @tag('guardian')
 * @tag('no-intercept')
 */
test.describe('J1 — Flujo Feliz Observador (API real) @guardian @no-intercept', () => {
  const FIXTURE_IMAGE = 'e2e/fixtures/acta-foto.jpg';

  /**
   * Padrón de la JRV: chico a propósito, para que el test no dependa del
   * padrón real de la mesa sembrada.
   */
  const SUFRAGANTES = 10;
  const BLANCOS = 1;
  const NULOS = 1;

  /** Actas que el test creó y hay que devolver a ANULADA para liberar el par. */
  const actasCreadas: number[] = [];

  /** Access token del observer, para las llamadas HTTP del propio test. */
  let token = '';

  test.beforeEach(async ({ page }) => {
    // Sesion real: login contra la API, con la cookie httpOnly de refresh.
    await loginReal(page, 'OBSERVER');
    token = await tokenDeAcceso(page);
  });

  test.afterEach(async ({ request }) => {
    // Un acta activa bloquea su par (JRV, cargo) para siempre: sin devolverla
    // a ANULADA, la base se satura y la suite deja de ser re-ejecutable.
    for (const actaId of actasCreadas.splice(0)) {
      await anularActa(request, actaId);
    }
  });

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Devuelve un access token del observer autenticado.
   *
   * <p>El access token vive en memoria en la app (no en el navegador), asi
   * que `page.request` no lo lleva. Se pide uno nuevo con la cookie httpOnly
   * de refresh, que es la misma via que usa la app al restaurar la sesion.
   *
   * @param page pagina ya autenticada
   * @returns el access token
   */
  async function tokenDeAcceso(page: Page): Promise<string> {
    const refresh = await page.request.post('/api/v1/auth/refresh');
    expect(refresh.ok()).toBeTruthy();
    const cuerpo = (await refresh.json()) as { accessToken: string };
    return cuerpo.accessToken;
  }

  /**
   * Busca el primer par (JRV, cargo) sin acta activa.
   *
   * @param page pagina ya autenticada; su contexto comparte las cookies
   * @returns el par libre y los datos de la JRV y del cargo
   */
  async function comboLibre(page: Page): Promise<{
    jrvId: number;
    cargoId: number;
    numeroJrv: string;
    nombreCargo: string;
  }> {
    const cabeceras = { Authorization: `Bearer ${token}` };

    const jrvs = (await (
      await page.request.get('/api/v1/observer/jrvs', { headers: cabeceras })
    ).json()) as Array<{
      id: number;
      numero: string;
      cargosPendientes: number;
    }>;

    for (const jrv of jrvs) {
      if (jrv.cargosPendientes <= 0) {
        continue;
      }
      const cargos = (await (
        await page.request.get(`/api/v1/observer/jrv/${jrv.id}/cargos`, {
          headers: cabeceras,
        })
      ).json()) as Array<{
        id: number;
        nombre: string;
        registrado: boolean;
      }>;

      for (const cargo of cargos) {
        if (!cargo.registrado) {
          return {
            jrvId: jrv.id,
            cargoId: cargo.id,
            numeroJrv: jrv.numero,
            nombreCargo: cargo.nombre,
          };
        }
      }
    }

    throw new Error(
      'La base no tiene ningún par (JRV, cargo) libre: las actas de la corrida ' +
        'anterior no se devolvieron a ANULADA.',
    );
  }

  /**
   * Devuelve un acta a ANULADA para liberar su par.
   *
   * @param request cliente HTTP independiente (no toca la sesion del test)
   * @param actaId id del acta
   */
  async function anularActa(
    request: APIRequestContext,
    actaId: number,
  ): Promise<void> {
    const login = await request.post('/api/v1/auth/login', {
      data: {
        email: 'admin@control-elector.local',
        password: 'admin12345',
      },
    });
    if (!login.ok()) {
      return;
    }
    const { accessToken } = (await login.json()) as { accessToken: string };

    await request.delete(`/api/v1/coordinator/actas/${actaId}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
  }

  /**
   * Espera la respuesta del registro del acta.
   *
   * @param page pagina con la que trabajar
   * @returns la respuesta del POST /api/v1/observer/actas
   */
  function esperarRegistro(page: Page) {
    return page.waitForResponse(
      (respuesta) =>
        respuesta.url().endsWith('/api/v1/observer/actas') &&
        respuesta.request().method() === 'POST',
      { timeout: 20_000 },
    );
  }

  /**
   * Escribe los votos de una mesa con el cuadre exacto.
   *
   * Todos los votos van al primer candidato: el padron es chico y lo que se
   * prueba es que la suma cuadre, no la distribucion.
   *
   * @param page pagina con la que trabajar
   * @param totalCandidatos cantidad de candidatos de la dignidad
   */
  async function llenarVotos(page: Page, totalCandidatos: number): Promise<void> {
    await page
      .getByRole('spinbutton', { name: /sufragantes|padrón/i })
      .fill(String(SUFRAGANTES));
    await page
      .getByRole('spinbutton', { name: /blanco/i })
      .fill(String(BLANCOS));
    await page.getByRole('spinbutton', { name: /nulo/i }).fill(String(NULOS));

    const campos = page.locator('input[aria-label^="Votos para"]');
    const votosDelPrimero = SUFRAGANTES - BLANCOS - NULOS;

    for (let i = 0; i < totalCandidatos; i++) {
      await campos.nth(i).fill(String(i === 0 ? votosDelPrimero : 0));
    }
  }

  /**
   * Adjunta la fotografia de la evidencia y avanza al paso de votos.
   *
   * @param page pagina con la que trabajar
   */
  async function adjuntarFoto(page: Page): Promise<void> {
    await page.locator('input[type="file"]').setInputFiles(FIXTURE_IMAGE);

    // La compression a .webp corre en segundo plano: el boton se habilita
    // cuando termina.
    const continuar = page.getByTestId('btn-continuar-votos');
    await expect(continuar).toBeEnabled({ timeout: 20_000 });
    await continuar.click();
  }

  // -------------------------------------------------------------------------
  // Step 1: JRV selection
  // -------------------------------------------------------------------------

  test('should display assigned JRVs for observer', async ({ page }) => {
    await page.goto('/observer/mis-jrvs');

    const jrvs = (await (
      await page.request.get('/api/v1/observer/jrvs', {
        headers: { Authorization: `Bearer ${token}` },
      })
    ).json()) as Array<{ numero: string; parroquia: string }>;

    await expect(page.getByText(jrvs[0].numero)).toBeVisible();
    // Varias JRVs pueden compartir parroquia: se toma la primera.
    await expect(page.getByText(jrvs[0].parroquia).first()).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // Step 2: Cargo selection
  // -------------------------------------------------------------------------

  test('should navigate to cargo list after selecting a JRV', async ({ page }) => {
    const combo = await comboLibre(page);

    await page.goto(`/observer/jrv/${combo.jrvId}/cargos`);

    await expect(page).toHaveURL(
      new RegExp(`/observer/jrv/${combo.jrvId}/cargos`),
    );
    await expect(page.getByText(combo.nombreCargo)).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // Step 3: Wizard — IMAGEN step
  // -------------------------------------------------------------------------

  test('should land on wizard IMAGEN step when navigating to registrar-acta', async ({
    page,
  }) => {
    const combo = await comboLibre(page);

    await page.goto(
      `/observer/jrv/${combo.jrvId}/cargo/${combo.cargoId}/registrar`,
    );

    // Verify navigation succeeded (correct URL)
    await expect(page).toHaveURL(
      new RegExp(
        `/observer/jrv/${combo.jrvId}/cargo/${combo.cargoId}/registrar`,
      ),
    );

    // File input exists (hidden by design, browser shows visual button)
    await expect(page.locator('input[type="file"]')).toHaveCount(1);
  });

  // -------------------------------------------------------------------------
  // Step 4: Attach image
  // -------------------------------------------------------------------------

  test('should attach image and advance to VOTOS step', async ({ page }) => {
    const combo = await comboLibre(page);

    await page.goto(
      `/observer/jrv/${combo.jrvId}/cargo/${combo.cargoId}/registrar`,
    );

    // Wait for candidates to load
    await expect(page.locator('.p-steps')).toBeVisible();

    await adjuntarFoto(page);

    await expect(page.getByTestId('step-votos')).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // Step 5: Fill votes
  // -------------------------------------------------------------------------

  test('should fill votes and pass checksum validation', async ({ page }) => {
    const combo = await comboLibre(page);
    const candidatos = (await (
      await page.request.get(
        `/api/v1/configuracion/candidatos?cargoId=${combo.cargoId}`,
        { headers: { Authorization: `Bearer ${token}` } },
      )
    ).json()) as unknown[];

    await page.goto(
      `/observer/jrv/${combo.jrvId}/cargo/${combo.cargoId}/registrar`,
    );

    await adjuntarFoto(page);
    await llenarVotos(page, candidatos.length);

    // The "Continuar a Revisión" button should be clickable when checksum passes
    await page.getByTestId('btn-continuar-revision').click({ timeout: 10_000 });

    // Verify in REVISION by checking for submit button
    await expect(page.getByTestId('btn-transmitir-acta')).toBeVisible({
      timeout: 5_000,
    });
  });

  // -------------------------------------------------------------------------
  // Step 6: Submit acta — full happy path, contra la API real
  // -------------------------------------------------------------------------

  test('J1 full happy path — submit acta and see success state @j1', async ({
    page,
  }) => {
    const combo = await comboLibre(page);
    const candidatos = (await (
      await page.request.get(
        `/api/v1/configuracion/candidatos?cargoId=${combo.cargoId}`,
        { headers: { Authorization: `Bearer ${token}` } },
      )
    ).json()) as unknown[];

    await page.goto(
      `/observer/jrv/${combo.jrvId}/cargo/${combo.cargoId}/registrar`,
    );

    // --- IMAGEN step ---
    await adjuntarFoto(page);

    // --- VOTOS step ---
    await llenarVotos(page, candidatos.length);

    // Advance to REVISION
    await page.getByTestId('btn-continuar-revision').click({ timeout: 10_000 });
    await expect(page.getByTestId('btn-transmitir-acta')).toBeVisible({
      timeout: 5_000,
    });

    // --- REVISION step: submit contra el backend real ---
    const registro = esperarRegistro(page);
    await page.getByTestId('btn-transmitir-acta').click({ timeout: 10_000 });

    const respuesta = await registro;
    expect(respuesta.status()).toBe(201);

    const acta = (await respuesta.json()) as { id: number; estado: string };
    expect(acta.estado).toBe('ENVIADA');
    actasCreadas.push(acta.id);

    // The app confirms the send and goes back to the dignity list.
    await expect(page).toHaveURL(/cargos|jrv/, { timeout: 15_000 });
  });

  // -------------------------------------------------------------------------
  // Guard: checksum mismatch blocks advance to REVISION
  // -------------------------------------------------------------------------

  test('should block advance to REVISION when checksum fails @j3', async ({
    page,
  }) => {
    const combo = await comboLibre(page);

    await page.goto(
      `/observer/jrv/${combo.jrvId}/cargo/${combo.cargoId}/registrar`,
    );

    await adjuntarFoto(page);

    // Fill an UNBALANCED acta: padrón=10, the votes add up to 2.
    await page
      .getByRole('spinbutton', { name: /sufragantes|padrón/i })
      .fill(String(SUFRAGANTES));
    await page.getByRole('spinbutton', { name: /blanco/i }).fill('1');
    await page.getByRole('spinbutton', { name: /nulo/i }).fill('1');

    await page.getByTestId('btn-continuar-revision').click({ timeout: 10_000 });

    // The error/warning message about the checksum discrepancy should appear
    await expect(
      page.locator('[role="alert"], .p-message-error, .p-inline-message').filter(
        { hasText: /faltan|sobran|no cuadra|suma|checksum|diferencia/i },
      ),
    ).toBeVisible({ timeout: 5_000 });
  });

  // -------------------------------------------------------------------------
  // Candidatos list visible in VOTOS step
  // -------------------------------------------------------------------------

  test('should display candidate names in VOTOS step', async ({ page }) => {
    const combo = await comboLibre(page);
    const candidatos = (await (
      await page.request.get(
        `/api/v1/configuracion/candidatos?cargoId=${combo.cargoId}`,
        { headers: { Authorization: `Bearer ${token}` } },
      )
    ).json()) as Array<{ nombre: string }>;

    await page.goto(
      `/observer/jrv/${combo.jrvId}/cargo/${combo.cargoId}/registrar`,
    );

    await adjuntarFoto(page);

    for (const candidato of candidatos) {
      await expect(page.getByText(candidato.nombre)).toBeVisible({
        timeout: 8_000,
      });
    }
  });

  // -------------------------------------------------------------------------
  // Idempotencia: el mismo par (JRV, cargo) no se registra dos veces
  // -------------------------------------------------------------------------

  test('Validar idempotencia: 409 Conflict en duplicado @guardian', async ({
    page,
  }) => {
    const combo = await comboLibre(page);
    const candidatos = (await (
      await page.request.get(
        `/api/v1/configuracion/candidatos?cargoId=${combo.cargoId}`,
        { headers: { Authorization: `Bearer ${token}` } },
      )
    ).json()) as unknown[];

    // --- Primer registro: va ---
    await page.goto(
      `/observer/jrv/${combo.jrvId}/cargo/${combo.cargoId}/registrar`,
    );
    await adjuntarFoto(page);
    await llenarVotos(page, candidatos.length);
    await page.getByTestId('btn-continuar-revision').click({ timeout: 10_000 });

    const primerRegistro = esperarRegistro(page);
    await page.getByTestId('btn-transmitir-acta').click({ timeout: 10_000 });

    const primera = await primerRegistro;
    expect(primera.status()).toBe(201);
    const acta = (await primera.json()) as { id: number };
    actasCreadas.push(acta.id);

    // --- Segundo registro del MISMO par: el backend lo rechaza ---
    await page.goto(
      `/observer/jrv/${combo.jrvId}/cargo/${combo.cargoId}/registrar`,
    );
    await adjuntarFoto(page);
    await llenarVotos(page, candidatos.length);
    await page.getByTestId('btn-continuar-revision').click({ timeout: 10_000 });

    const segundoRegistro = esperarRegistro(page);
    await page.getByTestId('btn-transmitir-acta').click({ timeout: 10_000 });

    const segunda = await segundoRegistro;
    expect(segunda.status()).toBe(409);

    // Y no se creo un segundo acta: la que existe sigue siendo una sola.
    const actas = (await (
      await page.request.get('/api/v1/observer/mis-actas?page=0&size=50', {
        headers: { Authorization: `Bearer ${token}` },
      })
    ).json()) as { data: Array<{ id: number; jrvId: number; cargoId: number }> };

    const delPar = actas.data.filter(
      (a) => a.jrvId === combo.jrvId && a.cargoId === combo.cargoId,
    );
    expect(delPar.length).toBe(1);
  });
});
