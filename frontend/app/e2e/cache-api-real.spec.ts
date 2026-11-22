import { expect, test } from '@playwright/test';
import { tokenDe } from './fixtures/actas-real';

/**
 * El backend, para las rutas que el proxy del dev server no reenvia.
 *
 * <p>El proxy de Angular (proxy.conf.json) reenvia `/api` a :8080, pero no
 * `/q/metrics`: las metricas se leen directamente del backend.
 */
const BACKEND = process.env['E2E_BACKEND_URL'] ?? 'http://localhost:8080';

/**
 * Caché de datos estáticos — contra la API real (@guardian @no-intercept).
 *
 * T-CACHE-STRAT-01 F.3/F.4, versión sin mocks.
 *
 * El spec `cache-datos-estaticos.spec.ts` intercepta la red: cuenta
 * peticiones del navegador, pero no toca el backend. Este journey va contra
 * la API real, y comprueba lo que solo se puede comprobar ahí:
 *
 *  - que las métricas del backend cuentan los aciertos y los fallos de verdad;
 *  - que una escritura invalida la caché (el cargo nuevo aparece de
 *    inmediato, sin esperar el TTL);
 *  - que el vaciado manual (MASTER) deja la caché vacía.
 *
 * Requiere el backend arriba en `http://localhost:8080` con la semilla de
 * datos. La matriz de roles es la misma que en `AdminCacheResourceTest`:
 * vaciar la caché es una escritura sobre el estado operativo.
 */
test.describe('Caché de datos estáticos — API real @guardian @no-intercept', () => {
  const MASTER = { email: 'mastersolon@example.local', password: 'admin12345' };

  /** Lee los contadores de caché del backend. */
  async function contadoresCache(
    request: import('@playwright/test').APIRequestContext,
  ): Promise<Record<string, { hits: number; misses: number }>> {
    const res = await request.get(`${BACKEND}/q/metrics`);
    expect(res.ok()).toBeTruthy();
    const cuerpo = await res.text();
    const porCache: Record<string, { hits: number; misses: number }> = {};
    for (const linea of cuerpo.split('\n')) {
      const m = linea.match(
        /^(cache_hits_total|cache_misses_total)\{cache="([^"]+)"\}\s+([\d.eE+-]+)/,
      );
      if (!m) continue;
      const valor = Number.parseFloat(m[3]);
      if (Number.isNaN(valor)) continue;
      porCache[m[2]] = porCache[m[2]] ?? { hits: 0, misses: 0 };
      if (m[1] === 'cache_hits_total') {
        porCache[m[2]].hits += valor;
      } else {
        porCache[m[2]].misses += valor;
      }
    }
    return porCache;
  }

  /**
   * Contadores de una cache, con 0 en lo que aun no existe.
   *
   * <p>En un backend recién arrancado `/q/metrics` todavía no publica los
   * contadores de una cache hasta que hay al menos una lectura: sin esto, el
   * test reventaría con un TypeError en vez de con su propia aserción.
   */
  function contadoresDe(
    contadores: Record<string, { hits: number; misses: number }>,
    cache: string,
  ): { hits: number; misses: number } {
    return contadores[cache] ?? { hits: 0, misses: 0 };
  }

  test('el backend cuenta los aciertos de la cache y la escritura la invalida', async ({
    request,
  }) => {
    const token = await tokenDe(request, MASTER.email, MASTER.password);
    const auth = { Authorization: `Bearer ${token}` };

    // 1) Vaciar la caché para partir de cero. Sin esto, el test dependería
    // del estado que dejó el test anterior: los tests comparten backend.
    //
    // Ojo: los contadores de /q/metrics son ACUMULADOS (vaciar la caché no
    // los reinicia), así que lo que se afirma son deltas, no valores.
    const vaciado = await request.get('/api/v1/admin/cache/clear', {
      headers: auth,
    });
    expect(vaciado.ok()).toBeTruthy();
    const cuerpoVaciado = (await vaciado.json()) as { clearedCaches: string[] };
    expect(cuerpoVaciado.clearedCaches).toContain('cargos');

    const antes = await contadoresCache(request);

    // 2) Primera lectura: es un fallo (la caché está vacía).
    await request.get('/api/v1/configuracion/cargos', { headers: auth });
    const trasLaPrimera = await contadoresCache(request);
    const antesCargos = contadoresDe(antes, 'cargos');
    const primeraCargos = contadoresDe(trasLaPrimera, 'cargos');
    expect(primeraCargos.hits - antesCargos.hits).toBe(0);
    expect(primeraCargos.misses - antesCargos.misses).toBeGreaterThan(0);

    // 3) Segunda lectura: es un acierto, y la base no se toca.
    await request.get('/api/v1/configuracion/cargos', { headers: auth });
    const trasLaSegunda = await contadoresCache(request);
    const segundaCargos = contadoresDe(trasLaSegunda, 'cargos');
    expect(segundaCargos.hits - primeraCargos.hits).toBe(1);
    // El acierto no suma fallos: el hit ratio no se deforma.
    expect(segundaCargos.misses).toBe(primeraCargos.misses);

    // 4) Crear un cargo: la escritura invalida la caché de cargos.
    const nombre = `CARGO E2E ${Date.now()}`;
    const creado = await request.post('/api/v1/configuracion/cargos', {
      headers: auth,
      data: { nombre, nivel: 'CANTON', puestos: 1 },
    });
    expect(creado.status()).toBe(201);
    const cargoCreado = (await creado.json()) as { id: number };

    try {
      // 5) El cargo nuevo aparece DE INMEDIATO: no esperamos al TTL.
      const listado = await request.get('/api/v1/configuracion/cargos', {
        headers: auth,
      });
      const cargos = (await listado.json()) as { id: number; nombre: string }[];
      expect(cargos.map((c) => c.nombre)).toContain(nombre);
    } finally {
      // Limpieza: borrar el cargo de prueba (tambien invalida la caché).
      await request.delete(`/api/v1/configuracion/cargos/${cargoCreado.id}`, {
        headers: auth,
      });
    }
  });

  test('el vaciado manual deja la cache vacia y queda auditado', async ({
    request,
  }) => {
    const token = await tokenDe(request, MASTER.email, MASTER.password);
    const auth = { Authorization: `Bearer ${token}` };

    // Calentar la caché: la primera lectura la llena (fallo), la segunda es
    // un acierto. Con una sola lectura el contador de aciertos seguiria en 0.
    await request.get('/api/v1/configuracion/partidos', { headers: auth });
    await request.get('/api/v1/configuracion/partidos', { headers: auth });
    const antes = await contadoresCache(request);
    expect(contadoresDe(antes, 'partidos').hits).toBeGreaterThan(0);

    // Vaciar.
    const vaciado = await request.get('/api/v1/admin/cache/clear', {
      headers: auth,
      params: { cacheName: 'partidos' },
    });
    expect(vaciado.ok()).toBeTruthy();
    const cuerpo = (await vaciado.json()) as { clearedCaches: string[] };
    expect(cuerpo.clearedCaches).toEqual(['partidos']);

    // La siguiente lectura vuelve a ser un fallo: la caché estaba vacía.
    await request.get('/api/v1/configuracion/partidos', { headers: auth });
    const despues = await contadoresCache(request);
    expect(despues['partidos'].misses).toBeGreaterThan(
      antes['partidos'].misses,
    );
  });

  test('sin token de MASTER, la cache no se vacia', async ({ request }) => {
    // Un rol que no es MASTER recibe 403 y la caché sigue como estaba.
    const token = await tokenDe(
      request,
      'observervera@example.local',
      'admin12345',
    );
    const antes = await contadoresCache(request);

    const intento = await request.get('/api/v1/admin/cache/clear', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(intento.status()).toBe(403);

    const despues = await contadoresCache(request);
    expect(contadoresDe(despues, 'cargos').hits).toBe(
      contadoresDe(antes, 'cargos').hits,
    );
  });
});
