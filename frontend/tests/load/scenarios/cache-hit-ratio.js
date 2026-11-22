/**
 * Cache Hit Ratio — 100 VUs sobre los endpoints de datos estáticos.
 * T-CACHE-STRAT-01 F.5.
 *
 * Qué mide:
 *  - Latencia p95 de las lecturas cacheadas (cargos, partidos, candidatos,
 *    geografía).
 *  - El hit ratio real, leído de las métricas Micrometer que publica el
 *    backend (`cache_hits_total` / `cache_misses_total` en `/q/metrics`),
 *    no una estimación: el dato sale del propio servidor.
 *
 * Por qué importa: el SDD exige hit ratio > 90% y p95 < 2s. Sin este script
 * esos dos números son promesas, no evidencia.
 *
 * Uso:
 *   k6 run scenarios/cache-hit-ratio.js
 *   MASTER_EMAIL=... MASTER_PASSWORD=... k6 run scenarios/cache-hit-ratio.js
 *   CACHE_ENABLED=false k6 run scenarios/cache-hit-ratio.js   # baseline sin cache
 *
 * El backend se levanta con la cache activada o desactivada y se corre el MISMO
 * script dos veces: la diferencia de hit ratio y de latencia es la mejora
 * atribuible a la cache.
 *
 * Como apagarla: `quarkus.cache.enabled=false` (variable de entorno
 * `QUARKUS_CACHE_ENABLED=false`). OJO: en Quarkus esa propiedad se fija en
 * tiempo de build, asi que hay que RECONSTRUIR la imagen del backend; poner la
 * variable en un pod ya construido no la apaga. La alternativa sin tocar la
 * imagen es bajar el TTL a 1s por cache:
 * `QUARKUS_CACHE_CAFFEINE_CARGOS_EXPIRE_AFTER_WRITE=1s` (y las otras tres).
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { config, apiUrl } from '../config.js';

/** 1 si el hit ratio final supera el objetivo, 0 si no. Se evaluan los umbrales al final. */
const hitRatioCumple = new Rate('cache_hit_ratio_ok');
/** Peticiones de lectura que devolvieron 200. */
const lecturasOk = new Counter('cache_lecturas_ok');

/** Objetivo del SDD: hit ratio > 90%. */
const HIT_RATIO_OBJETIVO = 0.9;

/** p95 < 2s (baseline del SDD de performance). */
const P95_OBJETIVO_MS = 2000;

/** Lecturas cacheadas del backend: 4 endpoints, 10 peticiones por VU (F.5). */
const ENDPOINTS = [
  { path: '/configuracion/cargos', tag: 'cargos', requiereToken: false },
  { path: '/configuracion/partidos', tag: 'partidos', requiereToken: false },
  { path: '/configuracion/candidatos', tag: 'candidatos', requiereToken: false },
  { path: '/master/geografia', tag: 'geografia', requiereToken: true },
];

/** Peticiones por VU (F.5: 10). */
const PETICIONES_POR_VU = 10;

export const options = {
  scenarios: {
    cache_hit_ratio: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 }, // ramp up de 10s
        { duration: '20s', target: 100 }, // meseta
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_duration: [`p(95)<${P95_OBJETIVO_MS}`],
    http_req_failed: ['rate<0.01'],
    cache_hit_ratio_ok: ['rate>=0.99'],
  },
};

/**
 * Lee los contadores de cache del backend.
 *
 * @returns {{hits: number, misses: number, porCache: object}} contadores acumulados
 */
function leerMetricasCache() {
  const res = http.get(`${config.BASE_URL}/q/metrics`, {
    tags: { endpoint: 'metrics' },
  });
  const porCache = {};
  let hits = 0;
  let misses = 0;

  if (res.status !== 200) {
    return { hits, misses, porCache };
  }

  for (const linea of res.body.split('\n')) {
    const m = linea.match(/^(cache_hits_total|cache_misses_total)\{cache="([^"]+)"\}\s+([\d.eE+-]+)/);
    if (!m) continue;
    const valor = parseFloat(m[3]);
    if (Number.isNaN(valor)) continue;
    const clave = m[2];
    porCache[clave] = porCache[clave] || { hits: 0, misses: 0 };
    if (m[1] === 'cache_hits_total') {
      hits += valor;
      porCache[clave].hits += valor;
    } else {
      misses += valor;
      porCache[clave].misses += valor;
    }
  }

  return { hits, misses, porCache };
}

export function setup() {
  const token = http.post(
    apiUrl('/auth/login'),
    JSON.stringify({
      email: config.master.email,
      password: config.master.password,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const inicial = leerMetricasCache();

  return {
    token: token.status === 200 ? token.json('accessToken') : null,
    base: inicial,
  };
}

export default function (data) {
  const headers = { 'Content-Type': 'application/json' };
  if (data.token) {
    headers.Authorization = `Bearer ${data.token}`;
  }

  for (let i = 0; i < PETICIONES_POR_VU; i++) {
    for (const endpoint of ENDPOINTS) {
      // La geografía exige `configuracion:read` (MASTER): si el token no
      // está, se salta ese endpoint en vez de contar un 401 como error.
      if (endpoint.requiereToken && !data.token) {
        continue;
      }

      const res = http.get(apiUrl(endpoint.path), {
        headers,
        tags: { endpoint: `cache-${endpoint.tag}` },
      });

      const ok = check(res, {
        [`${endpoint.tag} status 200`]: (r) => r.status === 200,
        [`${endpoint.tag} latencia < 2s`]: (r) => r.timings.duration < P95_OBJETIVO_MS,
      });

      if (ok) {
        lecturasOk.add(1);
      }
    }
  }

  sleep(0.2);
}

export function teardown(data) {
  const final = leerMetricasCache();
  const hits = final.hits - data.base.hits;
  const misses = final.misses - data.base.misses;
  const total = hits + misses;

  const ratio = total > 0 ? hits / total : 0;

  console.log('─── Resumen de cache (T-CACHE-STRAT-01 F.5) ───');
  console.log(`hits=${hits} misses=${misses} total=${total}`);
  console.log(`hit ratio = ${(ratio * 100).toFixed(2)}% (objetivo > 90%)`);
  for (const clave of Object.keys(final.porCache)) {
    console.log(`  ${clave}: ${JSON.stringify(final.porCache[clave])}`);
  }
  console.log('─────────────────────────────────────────────');

  hitRatioCumple.add(ratio >= HIT_RATIO_OBJETIVO ? 1 : 0);
}

/**
 * Resumen legible para el reporte: el hit ratio y la latencia p95.
 * @param {object} data resumen que produce k6
 * @returns {object} archivos que k6 escribe al terminar
 */
export function handleSummary(data) {
  const ratio = data.metrics.cache_hit_ratio_ok
    ? data.metrics.cache_hit_ratio_ok.values.rate
    : 0;
  const p95 = data.metrics.http_req_duration
    ? data.metrics.http_req_duration.values['p(95)']
    : 0;

  const linea = [
    '# Cache Hit Ratio — T-CACHE-STRAT-01 F.5',
    '',
    `- p95: ${p95} ms (objetivo < ${P95_OBJETIVO_MS} ms)`,
    `- hit ratio: ${(ratio * 100).toFixed(2)}% (objetivo > ${HIT_RATIO_OBJETIVO * 100}%)`,
    `- peticiones fallidas: ${data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : 0}`,
    '',
  ].join('\n');

  return {
    stdout: linea + '\n',
    'results/cache-hit-ratio-summary.md': linea,
    'results/cache-hit-ratio-summary.json': JSON.stringify(data, null, 2),
  };
}
