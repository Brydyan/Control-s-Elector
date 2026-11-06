/**
 * Common utilities for k6 load tests
 * T-INFRA-LOAD-01: shared functions, helpers, assertions
 */

import http from 'k6/http';
import { check, fail } from 'k6';
import { config, apiUrl } from './config.js';

/**
 * Login and return JWT token
 * @param {object} credentials - {email, password}
 * @param {object} tags - k6 tags for metrics
 * @returns {string} JWT access token
 */
export function login(credentials, tags = {}) {
  const payload = JSON.stringify({
    email: credentials.email,
    password: credentials.password,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      endpoint: 'login',
      ...tags,
    },
  };

  const response = http.post(apiUrl('/auth/login'), payload, params);

  check(response, {
    'login status 200': (r) => r.status === 200,
    'login has accessToken': (r) => r.json('accessToken') !== undefined,
  });

  if (response.status !== 200) {
    fail(`Login failed with status ${response.status}`);
  }

  return response.json('accessToken');
}

/**
 * Build authorization headers
 * @param {string} token - JWT token
 * @returns {object} Headers object
 */
export function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

/**
 * Get observer's assigned JRVs (or default to random)
 * @param {string} token - JWT token
 * @param {object} tags - k6 tags
 * @returns {array} Array of JRV objects
 */
export function getObserverJrvs(token, tags = {}) {
  const params = {
    headers: authHeaders(token),
    tags: {
      endpoint: 'get-jrvs',
      ...tags,
    },
  };

  const response = http.get(apiUrl('/observer/mis-jrvs'), params);

  check(response, {
    'get-jrvs status 200': (r) => r.status === 200,
    'get-jrvs has content': (r) => Array.isArray(r.json()),
  });

  if (response.status !== 200 || !Array.isArray(response.json())) {
    fail(`Failed to get observer JRVs: status ${response.status}`);
  }

  return response.json();
}

/**
 * Register acta (registrar voto)
 * @param {string} token - JWT token
 * @param {object} acta - Acta object
 * @param {object} tags - k6 tags
 * @returns {object} Response body
 */
export function registrarActa(token, acta, tags = {}) {
  const payload = JSON.stringify(acta);

  const params = {
    headers: authHeaders(token),
    tags: {
      endpoint: 'registrar-acta',
      ...tags,
    },
  };

  const response = http.post(apiUrl('/observer/actas'), payload, params);

  check(response, {
    'registrar-acta status 201': (r) => r.status === 201,
    'registrar-acta has id': (r) => r.json('id') !== undefined,
    'registrar-acta response time < 5s': (r) => r.timings.duration < 5000,
  });

  if (response.status !== 201) {
    console.error(
      `Registrar Acta failed: ${response.status} - ${response.body}`
    );
  }

  return response.status === 201 ? response.json() : null;
}

/**
 * List actas with pagination and filters
 * @param {string} token - JWT token
 * @param {object} params - {estado, page, size, sort, dir}
 * @param {object} tags - k6 tags
 * @returns {object} Paginated response
 */
export function listarActas(token, params = {}, tags = {}) {
  const defaults = {
    estado: 'ENVIADA',
    page: 0,
    size: 20,
    sort: 'createdAt',
    dir: 'DESC',
  };

  const queryParams = { ...defaults, ...params };
  const queryString = new URLSearchParams(queryParams).toString();

  const httpParams = {
    headers: authHeaders(token),
    tags: {
      endpoint: 'listar-actas',
      ...tags,
    },
  };

  const response = http.get(
    apiUrl(`/coordinator/actas?${queryString}`),
    httpParams
  );

  check(response, {
    'listar-actas status 200': (r) => r.status === 200,
    'listar-actas has content': (r) => Array.isArray(r.json('content')),
    'listar-actas response time < 5s': (r) => r.timings.duration < 5000,
  });

  if (response.status !== 200) {
    console.error(
      `Listar Actas failed: ${response.status} - ${response.body}`
    );
  }

  return response.status === 200 ? response.json() : null;
}

/**
 * Get acta detail by ID
 * @param {string} token - JWT token
 * @param {number} actaId - Acta ID
 * @param {object} tags - k6 tags
 * @returns {object} Acta detail
 */
export function getActaDetail(token, actaId, tags = {}) {
  const params = {
    headers: authHeaders(token),
    tags: {
      endpoint: 'get-acta-detail',
      ...tags,
    },
  };

  const response = http.get(apiUrl(`/coordinator/actas/${actaId}`), params);

  check(response, {
    'get-acta-detail status 200': (r) => r.status === 200,
  });

  return response.status === 200 ? response.json() : null;
}

/**
 * Get dashboard resumen (aggregated stats)
 * @param {string} token - JWT token
 * @param {object} tags - k6 tags
 * @returns {object} Dashboard data
 */
export function getDashboard(token, tags = {}) {
  const params = {
    headers: authHeaders(token),
    tags: {
      endpoint: 'dashboard',
      ...tags,
    },
  };

  const response = http.get(apiUrl('/dashboard/resumen'), params);

  check(response, {
    'dashboard status 200': (r) => r.status === 200,
    'dashboard response time < 3s': (r) => r.timings.duration < 3000,
  });

  return response.status === 200 ? response.json() : null;
}

/**
 * Sleep with random jitter to simulate realistic user behavior
 * @param {number} min - Minimum seconds
 * @param {number} max - Maximum seconds
 */
export function randomSleep(min = 1, max = 3) {
  const sleep = require('k6').sleep;
  const duration = min + Math.random() * (max - min);
  sleep(duration);
}

/**
 * Build SLA thresholds for k6 options
 * @param {string} scenario - Scenario name (baseline, rampUp, pico, stress)
 * @returns {object} Thresholds object for k6
 */
export function buildThresholds(scenario) {
  const baseThresholds = {
    // Overall request metrics
    'http_req_duration': ['p(95)<8000', 'p(99)<15000'],
    'http_req_failed': ['rate<0.2'], // 20% acceptable in stress

    // Endpoint-specific latency
    'http_req_duration{endpoint:login}': [
      `p(95)<${config.slas.login.p95}`,
      `p(99)<${config.slas.login.p99}`,
    ],
    'http_req_duration{endpoint:registrar-acta}': [
      `p(95)<${config.slas.registrarActa.p95}`,
      `p(99)<${config.slas.registrarActa.p99}`,
    ],
    'http_req_duration{endpoint:listar-actas}': [
      `p(95)<${config.slas.listarActas.p95}`,
      `p(99)<${config.slas.listarActas.p99}`,
    ],
    'http_req_duration{endpoint:dashboard}': [
      `p(95)<${config.slas.dashboard.p95}`,
      `p(99)<${config.slas.dashboard.p99}`,
    ],

    // Endpoint-specific failures
    'http_req_failed{endpoint:login}': [
      `rate<${config.slas.login.failureRate}`,
    ],
    'http_req_failed{endpoint:registrar-acta}': [
      `rate<${config.slas.registrarActa.failureRate}`,
    ],
    'http_req_failed{endpoint:listar-actas}': [
      `rate<${config.slas.listarActas.failureRate}`,
    ],

    // Minimum throughput
    'http_reqs': ['count>=100'],
  };

  // Scenario-specific overrides
  switch (scenario) {
    case 'baseline':
      return {
        ...baseThresholds,
        'http_req_duration': ['p(95)<2000', 'p(99)<3000'],
        'http_req_failed': ['rate<0.02'],
      };

    case 'rampUp':
      return {
        ...baseThresholds,
        'http_req_duration': ['p(95)<5000', 'p(99)<7000'],
        'http_req_failed': ['rate<0.05'],
      };

    case 'pico':
      return {
        ...baseThresholds,
        'http_req_duration': ['p(95)<5000', 'p(99)<8000'],
        'http_req_failed': ['rate<0.1'], // 10% acceptable in pico
      };

    case 'stress':
      return {
        ...baseThresholds,
        'http_req_duration': ['p(95)<10000', 'p(99)<15000'],
        'http_req_failed': ['rate<0.2'], // 20% acceptable in stress
      };

    default:
      return baseThresholds;
  }
}

/**
 * Log test summary info
 * @param {string} scenarioName - Test scenario name
 */
export function logScenarioStart(scenarioName) {
  console.log(`\n${'='.repeat(70)}`);
  console.log(`Iniciando: ${scenarioName}`);
  console.log(`Timestamp: ${new Date().toISOString()}`);
  console.log(`Base URL: ${config.BASE_URL}`);
  console.log(`${'='.repeat(70)}\n`);
}

export default {
  login,
  authHeaders,
  getObserverJrvs,
  registrarActa,
  listarActas,
  getActaDetail,
  getDashboard,
  randomSleep,
  buildThresholds,
  logScenarioStart,
};
