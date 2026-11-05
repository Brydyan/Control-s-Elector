import { Page } from '@playwright/test';

/** Base URL of the API as seen by the Angular app (matches environment.apiUrl). */
const API = '**/api/v1';

// ---------------------------------------------------------------------------
// Seed data — mirrors what DataSeeder creates on a fresh dev environment
// ---------------------------------------------------------------------------

export const MASTER_USER = {
  email: 'admsismas@control.ttcs',
  password: 'admin12345',
  nombre: 'Admin',
  apellido: 'Sistema',
  rol: 'MASTER',
};

export const OBSERVER_USER = {
  email: 'juaperobs@control.ttcs',
  password: 'obs12345',
  nombre: 'Juan',
  apellido: 'Pérez',
  rol: 'OBSERVER',
};

export const COORDINATOR_USER = {
  email: 'margarco@control.ttcs',  // María García COORDINATOR
  password: 'coord12345',
  nombre: 'María',
  apellido: 'García',
  rol: 'COORDINATOR',
};

// ---------------------------------------------------------------------------
// Canonical JWT stubs (HS256 structure — not verified by the frontend)
// ---------------------------------------------------------------------------

function stubToken(rol: string, sub: string): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(
    JSON.stringify({
      sub,
      rol,
      iss: 'control-elector',
      exp: Math.floor(Date.now() / 1000) + 3600,
    }),
  );
  return `${header}.${payload}.stub-signature`;
}

export const TOKENS = {
  MASTER: stubToken('MASTER', MASTER_USER.email),
  OBSERVER: stubToken('OBSERVER', OBSERVER_USER.email),
  COORDINATOR: stubToken('COORDINATOR', COORDINATOR_USER.email),
};

// ---------------------------------------------------------------------------
// Auth mocks
// ---------------------------------------------------------------------------

export async function mockAuthLogin(
  page: Page,
  user: typeof MASTER_USER,
): Promise<void> {
  const token = TOKENS[user.rol as keyof typeof TOKENS];
  await page.route(`${API}/auth/login`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: token,
        tokenType: 'Bearer',
        expiresIn: 900,
      }),
    });
  });
}

export async function mockAuthMe(
  page: Page,
  user: typeof MASTER_USER,
): Promise<void> {
  await page.route(`${API}/auth/me`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        email: user.email,
        nombre: user.nombre,
        apellido: user.apellido,
        rol: user.rol,
      }),
    });
  });
}

export async function mockMenusMe(page: Page, rol: string): Promise<void> {
  const menus: Record<string, object[]> = {
    OBSERVER: [
      { label: 'Mis JRVs', icon: 'pi pi-list', routerLink: '/observer/mis-jrvs' },
    ],
    COORDINATOR: [
      { label: 'Disputas', icon: 'pi pi-exclamation-triangle', routerLink: '/coordinator/disputas' },
    ],
    MASTER: [
      { label: 'Dashboard', icon: 'pi pi-chart-bar', routerLink: '/dashboard' },
      { label: 'Configuración', icon: 'pi pi-cog', routerLink: '/master/configuracion' },
    ],
  };

  await page.route(`${API}/menus/me`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(menus[rol] ?? []),
    });
  });
}

// ---------------------------------------------------------------------------
// Observer mocks
// ---------------------------------------------------------------------------

export const STUB_JRV_1 = {
  id: 1,
  nombre: 'JRV-001',
  parroquia: 'La Libertad',
  recinto: 'Escuela 24 de Mayo',
  mesa: '001',
  estado: 'PENDIENTE',
};

export const STUB_CARGO_1 = {
  id: 10,
  nombre: 'Alcalde',
  tipo: 'UNINOMINAL',
  estado: 'PENDIENTE',
};

export const STUB_CANDIDATOS = [
  {
    id: 101,
    nombre: 'Carlos Rodríguez',
    orden: 1,
    cargoId: 10,
    partidoId: 1,
    partidoNombre: 'Movimiento Ciudadano',
    partidoSiglas: 'MC',
    partidoNumeroLista: 5,
  },
  {
    id: 102,
    nombre: 'Ana López',
    orden: 2,
    cargoId: 10,
    partidoId: 2,
    partidoNombre: 'Alianza País',
    partidoSiglas: 'AP',
    partidoNumeroLista: 7,
  },
];

export const STUB_PARTIDOS = [
  { id: 1, nombre: 'Movimiento Ciudadano', siglas: 'MC', numeroLista: 5 },
  { id: 2, nombre: 'Alianza País', siglas: 'AP', numeroLista: 7 },
];

export async function mockObserverJrvs(page: Page): Promise<void> {
  await page.route(`${API}/observer/jrvs`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([STUB_JRV_1]),
    });
  });
}

export async function mockObserverCargos(page: Page): Promise<void> {
  await page.route(`${API}/observer/jrv/*/cargos`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([STUB_CARGO_1]),
    });
  });
}

export async function mockConfiguracionCandidatos(page: Page): Promise<void> {
  await page.route(`${API}/configuracion/candidatos*`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(STUB_CANDIDATOS),
    });
  });
}

export async function mockConfiguracionPartidos(page: Page): Promise<void> {
  await page.route(`${API}/configuracion/partidos*`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(STUB_PARTIDOS),
    });
  });
}

export const STUB_ACTA_BORRADOR = {
  id: 999,
  estado: 'BORRADOR',
  jrvId: STUB_JRV_1.id,
  cargoId: STUB_CARGO_1.id,
};

export const STUB_ACTA_ENVIADA = {
  id: 999,
  estado: 'ENVIADA',
  jrvId: STUB_JRV_1.id,
  cargoId: STUB_CARGO_1.id,
};

export async function mockActaBorrador(page: Page): Promise<void> {
  await page.route(`${API}/observer/actas/borrador`, async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify(STUB_ACTA_BORRADOR),
    });
  });
}

export async function mockActaEnvio(page: Page, status = 201): Promise<void> {
  await page.route(`${API}/observer/actas`, async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(
        status === 201 ? STUB_ACTA_ENVIADA : { error: 'Conflict', status: 409 },
      ),
    });
  });
}

export async function mockImagenUpload(page: Page, status = 200): Promise<void> {
  await page.route(`${API}/observer/actas/*/imagen`, async (route) => {
    await route.fulfill({ status, body: '' });
  });
}
