import { Page } from '@playwright/test';

import { versionTerminosVigente } from './consent';

/** Base URL of the API as seen by the Angular app (matches environment.apiUrl). */
const API = '**/api/v1';

// ---------------------------------------------------------------------------
// Seed data — mirrors what the backend seeds on a fresh dev environment
// ---------------------------------------------------------------------------

export const MASTER_USER = {
  email: 'admin@control-elector.local',
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

/**
 * Fecha ISO de hace `dias` dias.
 *
 * @param dias cantidad de dias hacia atras
 * @returns la fecha en ISO 8601
 */
function haceDias(dias: number): string {
  return new Date(Date.now() - dias * 24 * 60 * 60 * 1000).toISOString();
}

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
        acceptedTermsVersion: versionTerminosVigente(),
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

/**
 * Mocks /auth/refresh to simulate session restoration after page reload.
 * This is called by the authGuard when the store is empty (fresh page load).
 *
 * Critical: Must be registered BEFORE any navigation, so the guard can find it.
 */
export async function mockAuthRefresh(
  page: Page,
  user: typeof MASTER_USER,
): Promise<void> {
  const token = TOKENS[user.rol as keyof typeof TOKENS];
  const currentUserDto = {
    email: user.email,
    nombre: user.nombre,
    apellido: user.apellido,
    rol: user.rol,
    permisos: [],
    /**
     * Sin esto, la app cree que el usuario nunca acepto los terminos y el
     * guard `authGuard` manda a `/terminos`: la navegacion del test nunca
     * llega a la pantalla que queria probar.
     */
    acceptedTermsVersion: versionTerminosVigente(),
  };

  await page.route(`${API}/auth/refresh`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: token,
        tokenType: 'Bearer',
        expiresInSeconds: 3600,
        sesionId: 'e2e-test-session-id',
        usuario: currentUserDto,
      }),
    });
  });
}

// ---------------------------------------------------------------------------
// Observer mocks
// ---------------------------------------------------------------------------

/**
 * Matches JrvConEstado interface (what the API returns).
 * @see frontend/app/src/app/features/observer/domain/models/observer.model.ts
 */
export const STUB_JRV_1 = {
  jrvId: 1,
  numero: 'JRV-001',
  parroquia: 'La Libertad',
  cargosRegistrados: 0,
  cargosPendientes: 1,
} as const;

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
  jrvId: STUB_JRV_1.jrvId,
  cargoId: STUB_CARGO_1.id,
};

export const STUB_ACTA_ENVIADA = {
  id: 999,
  estado: 'ENVIADA',
  jrvId: STUB_JRV_1.jrvId,
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

// ---------------------------------------------------------------------------
// Coordinator mocks (J4 — Dispute/Correction flow)
// ---------------------------------------------------------------------------

/**
 * Mock de `GET /api/v1/coordinator/actas`.
 *
 * <p>La bandeja del coordinator vive en `/api/v1/coordinator/actas`, no en
 * `/api/v1/observer/actas`: mockear la ruta equivocada deja la peticion real
 * salir a la red sin que nadie la intercepte.
 *
 * @param page pagina con la que trabajar
 */
export async function mockCoordinatorMisActas(page: Page): Promise<void> {
  await page.route(`${API}/coordinator/actas`, async (route) => {
    if (route.request().method() !== 'GET') {
      await route.continue();
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        paginar([
          crearActaStub({
            id: 999,
            estado: 'ENVIADA',
            totalSufragantes: 300,
            votosBlancos: 30,
            votosNulos: 20,
            totalVotosCandidatos: 250,
          }),
        ]),
      ),
    });
  });
}

// ---------------------------------------------------------------------------
// Master/Admin mocks (J5 — Dashboard monitoring)
// ---------------------------------------------------------------------------

export async function mockCantonReportes(page: Page): Promise<void> {
  await page.route(`${API}/coordinator/reportes`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        cantons: [
          {
            cantonId: 1,
            nombre: 'Los Ríos',
            jrvCount: 42,
            actasEnviadas: 28,
            actasDisputadas: 3,
            actasAceptadas: 25,
            actasNulas: 2,
            totalSufragantes: 12500,
            totalVotos: 12480,
          },
          {
            cantonId: 2,
            nombre: 'Manabí',
            jrvCount: 38,
            actasEnviadas: 22,
            actasDisputadas: 2,
            actasAceptadas: 20,
            actasNulas: 1,
            totalSufragantes: 11200,
            totalVotos: 11195,
          },
        ],
      }),
    });
  });
}

// ---------------------------------------------------------------------------
// Observer Mis Actas mocks (T-FE-06 E2E Phase C)
// ---------------------------------------------------------------------------

/**
 * Estados reales de un acta.
 *
 * <p>Es el enum `EstadoActa` del backend: BORRADOR → ENVIADA → EN_DISPUTA →
 * CORREGIDA → APROBADA → ANULADA. Un mock con `TRANSMITIDO` o `REVISION` no
 * describe nada que la API pueda devolver.
 */
export const ESTADOS_ACTA = [
  'BORRADOR',
  'ENVIADA',
  'EN_DISPUTA',
  'CORREGIDA',
  'APROBADA',
  'ANULADA',
] as const;

export type EstadoActa = (typeof ESTADOS_ACTA)[number];

/**
 * Forma de `ActaResponseDto`, el DTO que devuelve el backend.
 *
 * <p>Los mocks tienen que hablar este idioma: si el stub inventa campos
 * (`numeroActa`, `accionLabel`, `urlEvidencia`) o inventa estados, el test
 * valida el mock y no el sistema.
 */
export interface ActaResponseStub {
  id: number;
  jrvId: number;
  cargoId: number;
  registradaPorId: number;
  estado: EstadoActa;
  totalSufragantes: number;
  votosBlancos: number;
  votosNulos: number;
  fechaRegistro: string;
  motivoAnulacion: string | null;
  nombreCargo: string;
  numeroJrv: string;
  totalVotosCandidatos: number | null;
}

/** Meta de `PagedResponse`: es lo que el backend devuelve en `meta`. */
export interface PageMetaStub {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Envoltura paginada: `{data, meta}`, la forma de `PagedResponse`. */
export interface PagedResponseStub<T> {
  data: T[];
  meta: PageMetaStub;
}

/**
 * Construye un acta con la forma del DTO del backend.
 *
 * @param overrides campos a overriding del stub base
 * @returns el acta con la forma de `ActaResponseDto`
 */
export function crearActaStub(
  overrides: Partial<ActaResponseStub> = {},
): ActaResponseStub {
  return {
    id: 1,
    jrvId: STUB_JRV_1.jrvId,
    cargoId: STUB_CARGO_1.id,
    registradaPorId: 4,
    estado: 'ENVIADA',
    totalSufragantes: 300,
    votosBlancos: 20,
    votosNulos: 10,
    fechaRegistro: new Date().toISOString(),
    motivoAnulacion: null,
    nombreCargo: STUB_CARGO_1.nombre,
    numeroJrv: STUB_JRV_1.numero,
    totalVotosCandidatos: 270,
    ...overrides,
  };
}

/**
 * Envuelve una lista en la envoltura paginada del backend.
 *
 * @param data elementos de la pagina
 * @param page pagina actual (base 0)
 * @param size tamano de la pagina
 * @returns el `PagedResponse` que devuelve la API
 */
export function paginar<T>(
  data: T[],
  page = 0,
  size = 10,
): PagedResponseStub<T> {
  return {
    data,
    meta: {
      page,
      size,
      totalElements: data.length,
      totalPages: data.length === 0 ? 0 : 1,
    },
  };
}

/**
 * Mock de `GET /api/v1/observer/mis-actas` con paginacion.
 *
 * <p>El backend responde con `PagedResponse` (`{data, meta}`), no con
 * `{content, currentPage}`: el envelope de Spring y el de Quarkus no son el
 * mismo, y el test tiene que hablar el del backend.
 *
 * @param page pagina con la que trabajar
 * @param options filtro por estado y status simulado
 */
export async function mockObserverMisActas(
  page: Page,
  options?: {
    estado?: string;
    page?: number;
    size?: number;
    status?: number;
  },
): Promise<void> {
  const { estado, page: pageNum = 0, size = 10, status = 200 } = options || {};

  const actas: ActaResponseStub[] = [
    crearActaStub({ id: 1, estado: 'ENVIADA', fechaRegistro: haceDias(2) }),
    crearActaStub({ id: 2, estado: 'ENVIADA', fechaRegistro: haceDias(1) }),
    crearActaStub({ id: 3, estado: 'EN_DISPUTA', fechaRegistro: haceDias(3) }),
    crearActaStub({ id: 4, estado: 'ENVIADA', fechaRegistro: haceDias(4) }),
    crearActaStub({ id: 5, estado: 'EN_DISPUTA', fechaRegistro: haceDias(5) }),
    crearActaStub({ id: 6, estado: 'ENVIADA', fechaRegistro: haceDias(6) }),
    crearActaStub({ id: 7, estado: 'APROBADA', fechaRegistro: haceDias(7) }),
    crearActaStub({ id: 8, estado: 'ENVIADA', fechaRegistro: haceDias(8) }),
    crearActaStub({ id: 9, estado: 'CORREGIDA', fechaRegistro: haceDias(9) }),
    crearActaStub({ id: 10, estado: 'ENVIADA', fechaRegistro: haceDias(10) }),
  ];

  await page.route(`${API}/observer/mis-actas*`, async (route) => {
    if (route.request().method() !== 'GET') {
      await route.continue();
      return;
    }

    const url = new URL(route.request().url());
    const queryEstado = url.searchParams.get('estado');
    const queryPage = parseInt(url.searchParams.get('page') || '0', 10);
    const querySize = parseInt(url.searchParams.get('size') || '10', 10);

    if (status !== 200) {
      await route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Server error', status }),
      });
      return;
    }

    // Filter by estado if provided
    let filtered = actas;
    if (queryEstado) {
      filtered = actas.filter((a) => a.estado === queryEstado);
    }

    // Paginate
    const totalElements = filtered.length;
    const offset = queryPage * querySize;
    const content = filtered.slice(offset, offset + querySize);
    const totalPages = querySize <= 0 ? 0 : Math.ceil(totalElements / querySize);

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: content,
        meta: {
          page: queryPage,
          size: querySize,
          totalElements,
          totalPages,
        },
      }),
    });
  });
}

/**
 * Mock de `GET /api/v1/observer/actas/{id}` con el detalle completo.
 *
 * <p>La ruta real es `/observer/actas/{id}`: `/observer/mis-actas/{id}` no
 * existe en el backend, y un mock de una ruta que nadie llama no prueba nada.
 *
 * @param page pagina con la que trabajar
 * @param actaId id del acta
 */
export async function mockObserverActaDetalle(
  page: Page,
  actaId: string,
): Promise<void> {
  const id = Number(actaId);
  const acta = crearActaStub({
    id: Number.isFinite(id) ? id : 1,
    estado: 'ENVIADA',
  });

  await page.route(`${API}/observer/actas/${actaId}`, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(acta),
      });
    } else {
      await route.continue();
    }
  });
}
