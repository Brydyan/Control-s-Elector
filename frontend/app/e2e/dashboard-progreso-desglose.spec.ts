import { expect } from '@playwright/test';
import { test } from './fixtures/auth.fixture';
import { loginReal } from './fixtures/auth.fixture';
import { tokenDe } from './fixtures/actas-real';

/**
 * Dashboard — Desglose de Parroquias contra la API real.
 *
 * @guardian @no-intercept
 *
 * Este journey existe porque el panel "Desglose de Parroquias" estuvo MESES
 * vacio sin que nadie lo notara: el frontend leia `parroquias` de
 * `GET /api/v1/dashboard/progreso`, el backend no lo mandaba y la capa de API
 * lo compensaba con un `?? []`. El contrato no assertaba el campo, el
 * rendering spec usaba un fixture y el panel se veia "vacio pero correcto".
 *
 * Contra la API real, este journey falla si:
 * - el backend deja de devolver `parroquias`;
 * - el panel deja de pintar filas;
 * - las filas del panel no coinciden con las que dice la API;
 * - la mini-barra de parroquia pierde el color por avance.
 */
test.describe('Dashboard — Desglose de Parroquias (API real) @guardian @no-intercept', () => {
  // La tabla de parroquias es de escritorio: en movil el layout cambia.
  test.skip(
    ({ viewport }) => !!viewport && viewport.width < 768,
    'Journey de escritorio: el desglose es una tabla.',
  );

  const MASTER = { email: 'admin@control-elector.local', password: 'admin12345' };

  test('should render one row per parroquia that the API reports', async ({ page, request }) => {
    // La API real, sin mocks: el mismo origen que usa el panel.
    const token = await tokenDe(request, MASTER.email, MASTER.password);
    const respuesta = await request.get('/api/v1/dashboard/progreso', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(respuesta.ok()).toBeTruthy();

    const cantones = (await respuesta.json()) as {
      cantonNombre: string;
      totalJrvs: number;
      jrvsConActa: number;
      parroquias: { parroquiaId: number; parroquiaNombre: string; avance: number }[];
    }[];

    // El backend tiene que traer el desglose: sin el, el panel no tiene nada que pintar.
    expect(cantones.length).toBeGreaterThan(0);
    const parroquiasDeLaApi = cantones.flatMap((c) => c.parroquias ?? []);
    expect(parroquiasDeLaApi.length).toBeGreaterThan(0);

    await loginReal(page, 'MASTER');
    await page.goto('/dashboard/progreso', { waitUntil: 'networkidle', timeout: 20_000 });

    // El encabezado del panel tiene que estar.
    await expect(page.getByText(/Desglose de Parroquias/)).toBeVisible();

    // Y el estado vacio NO: hay parroquias que mostrar.
    await expect(page.getByTestId('parroquias-empty-state')).toHaveCount(0);

    // Una fila por parroquia del canton seleccionado (por defecto, el primero).
    const filas = page.locator('[data-testid^="parroquia-row-"]');
    const primerCanton = cantones[0];
    await expect(filas).toHaveCount(primerCanton.parroquias.length);

    // Los nombres que muestra la tabla son los que dice la API.
    for (const parroquia of primerCanton.parroquias) {
      await expect(
        page.getByTestId(`parroquia-row-${parroquia.parroquiaId}`),
      ).toContainText(parroquia.parroquiaNombre);
    }

    // La mini-barra lleva el color de su avance (antes era verde fijo).
    const primeraFila = page.getByTestId(`parroquia-row-${primerCanton.parroquias[0].parroquiaId}`);
    const colorEsperado =
      primerCanton.parroquias[0].avance >= 80
        ? 'fill-green'
        : primerCanton.parroquias[0].avance >= 50
          ? 'fill-blue'
          : 'fill-amber';
    await expect(primeraFila.locator('.avance-cell')).toHaveClass(new RegExp(colorEsperado));
  });

  test('should keep the canton totals consistent with the sum of its parroquias', async ({
    request,
  }) => {
    // La conciliacion entre los dos niveles es la invariante que impide que un
    // cambio de semantica en uno de los dos pase inadvertido.
    const token = await tokenDe(request, MASTER.email, MASTER.password);
    const respuesta = await request.get('/api/v1/dashboard/progreso', {
      headers: { Authorization: `Bearer ${token}` },
    });
    const cantones = (await respuesta.json()) as {
      totalJrvs: number;
      jrvsConActa: number;
      parroquias: { jrvsEscrutadas: number; jrvsTotal: number }[];
    }[];

    for (const canton of cantones) {
      const sumaTotal = canton.parroquias.reduce((acc, p) => acc + p.jrvsTotal, 0);
      const sumaEscrutadas = canton.parroquias.reduce((acc, p) => acc + p.jrvsEscrutadas, 0);
      expect(sumaTotal).toBe(canton.totalJrvs);
      expect(sumaEscrutadas).toBe(canton.jrvsConActa);
    }
  });
});
