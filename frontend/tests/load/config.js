/**
 * Load Testing Configuration for Control Electoral
 * T-INFRA-LOAD-01: k6 configuration & test data
 */

export const config = {
  // Backend API
  BASE_URL: `http://${__ENV.API_HOST || 'localhost:8080'}`,
  API_PATH: '/api/v1',

  // Timeout defaults (ms)
  timeout: 30000,

  // Test credentials (must exist in DB before running tests)
  observers: [
    { id: 'obs-1', email: 'obs1@electoral.ec', password: 'SecurePass123!' },
    { id: 'obs-2', email: 'obs2@electoral.ec', password: 'SecurePass123!' },
    { id: 'obs-3', email: 'obs3@electoral.ec', password: 'SecurePass123!' },
    { id: 'obs-4', email: 'obs4@electoral.ec', password: 'SecurePass123!' },
    { id: 'obs-5', email: 'obs5@electoral.ec', password: 'SecurePass123!' },
  ],

  coordinators: [
    { id: 'coord-1', email: 'coord1@electoral.ec', password: 'SecurePass123!' },
    { id: 'coord-2', email: 'coord2@electoral.ec', password: 'SecurePass123!' },
  ],

  admin: {
    id: 'admin',
    email: 'admin@electoral.ec',
    password: 'SecurePass123!',
  },

  // Election data fixtures
  election: {
    totalJrvs: 500,
    cargosPerJrv: [1, 2, 3], // Cargo IDs: provincial, cantonal, parroquial

    // Candidatos per cargo
    candidatosPerCargo: {
      1: 5, // Provincial: 5 candidatos
      2: 8, // Cantonal: 8 candidatos
      3: 12, // Parroquial: 12 candidatos
    },

    // Province/canton/parroquia (realistic for Santa Elena)
    provinces: [
      { id: 1, name: 'Santa Elena' },
    ],
    cantons: [
      { id: 2401, name: 'Santa Elena' },
      { id: 2402, name: 'La Libertad' },
      { id: 2403, name: 'Salinas' },
    ],
  },

  // SLA Thresholds
  slas: {
    login: {
      name: 'Login',
      p95: 1500,
      p99: 2000,
      failureRate: 0.02, // <2%
    },
    registrarActa: {
      name: 'Registrar Acta',
      p95: 5000,
      p99: 8000,
      failureRate: 0.05, // <5%
    },
    listarActas: {
      name: 'Listar Actas',
      p95: 3000,
      p99: 5000,
      failureRate: 0.05, // <5%
    },
    subirEvidencia: {
      name: 'Subir Evidencia',
      p95: 6000,
      p99: 10000,
      failureRate: 0.05, // <5%
    },
    dashboard: {
      name: 'Dashboard',
      p95: 3000,
      p99: 5000,
      failureRate: 0.05, // <5%
    },
  },

  // Test scenarios
  scenarios: {
    baseline: {
      name: 'Baseline - 10 VUs, 5 min',
      description: 'Verify system is healthy under minimal load',
      vus: 10,
      duration: '5m',
    },
    rampUp: {
      name: 'Ramp-up - 0→100 VUs',
      description: 'Gradual load increase to identify degradation point',
      stages: [
        { duration: '2m', target: 20 },
        { duration: '5m', target: 50 },
        { duration: '8m', target: 100 },
        { duration: '3m', target: 0 },
      ],
    },
    pico: {
      name: 'Pico Electoral - 0→150 VUs',
      description: 'Full electoral scenario: 17:00-17:30 votación cierre',
      stages: [
        { duration: '2m', target: 20 },
        { duration: '10m', target: 100 },
        { duration: '10m', target: 150 }, // Peak: all JRVs sending actas
        { duration: '5m', target: 50 },   // Ramp down
        { duration: '3m', target: 0 },    // Cool-off
      ],
    },
    stress: {
      name: 'Stress Test - 200 VUs sustained',
      description: 'Find breaking point (unrealistic but useful)',
      vus: 200,
      duration: '30m',
    },
  },
};

/**
 * Build full URL for API endpoint
 * @param {string} path - API path (e.g., '/observer/actas')
 * @returns {string} Full URL
 */
export function apiUrl(path) {
  return `${config.BASE_URL}${config.API_PATH}${path}`;
}

/**
 * Get test credentials for observer (round-robin)
 * @param {number} vusId - Virtual user ID
 * @returns {object} Observer credentials
 */
export function getObserverCredentials(vusId) {
  return config.observers[vusId % config.observers.length];
}

/**
 * Get test credentials for coordinator
 * @param {number} vusId - Virtual user ID
 * @returns {object} Coordinator credentials
 */
export function getCoordinatorCredentials(vusId) {
  return config.coordinators[vusId % config.coordinators.length];
}

/**
 * Get random JRV ID
 * @returns {number} JRV ID (1 to totalJrvs)
 */
export function getRandomJrvId() {
  return Math.floor(Math.random() * config.election.totalJrvs) + 1;
}

/**
 * Get random cargo ID
 * @returns {number} Cargo ID
 */
export function getRandomCargoId() {
  return config.election.cargosPerJrv[
    Math.floor(Math.random() * config.election.cargosPerJrv.length)
  ];
}

/**
 * Get number of candidatos for cargo
 * @param {number} cargoId - Cargo ID
 * @returns {number} Number of candidatos
 */
export function getCandidatoCountForCargo(cargoId) {
  return config.election.candidatosPerCargo[cargoId] || 5;
}

/**
 * Generate random votos (respecting total sufragantes)
 * @param {number} totalSufragantes - Total voters
 * @param {number} candidatoCount - Number of candidatos
 * @returns {array} Array of {candidatoId, votos}
 */
export function generateVotos(totalSufragantes, candidatoCount) {
  const detalles = [];
  let remaining = totalSufragantes;

  for (let i = 0; i < candidatoCount; i++) {
    const votos = Math.floor(Math.random() * (remaining / (candidatoCount - i)));
    detalles.push({
      candidatoId: i + 1,
      votos: votos,
    });
    remaining -= votos;
  }

  // Distribute remaining votos randomly
  if (remaining > 0) {
    const idx = Math.floor(Math.random() * candidatoCount);
    detalles[idx].votos += remaining;
  }

  return detalles;
}

export default config;
