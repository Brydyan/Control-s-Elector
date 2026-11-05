/**
 * Realistic Mixed Traffic: Pico Electoral
 * T-INFRA-LOAD-01: Scenario 4 (CRITICAL - Most Realistic)
 *
 * Purpose: Simulate actual election scenario
 * - 30% Observer registering actas (30 req/min in pico)
 * - 40% Coordinator reviewing actas (20 req/min in pico)
 * - 10% Login/session refresh (8 req/min in pico)
 * - 20% Admin checking dashboard (4 req/min in pico)
 *
 * Stages: Ramp-up → Pico (17:00-17:30) → Ramp-down → Cool-off
 *
 * Run: k6 run scenarios/mixed-realistic.js
 */

import { sleep } from 'k6';
import {
  config,
  getObserverCredentials,
  getCoordinatorCredentials,
  getRandomJrvId,
  getRandomCargoId,
  getCandidatoCountForCargo,
  generateVotos,
} from '../config.js';
import {
  login,
  registrarActa,
  listarActas,
  getActaDetail,
  getDashboard,
  buildThresholds,
  randomSleep,
  logScenarioStart,
} from '../common.js';

export const options = {
  stages: [
    {
      duration: '2m',
      target: 20,
      name: 'Ramp-up phase: 0 → 20 users',
    },
    {
      duration: '10m',
      target: 100,
      name: 'Ramp to pico: 20 → 100 users',
    },
    {
      duration: '10m',
      target: 150,
      name: 'Peak load: sustained at 150 users (17:00-17:30 pico)',
    },
    {
      duration: '5m',
      target: 50,
      name: 'Ramp-down: 150 → 50 users',
    },
    {
      duration: '3m',
      target: 0,
      name: 'Cool-off: 50 → 0 users',
    },
  ],

  thresholds: buildThresholds('pico'),

  ext: {
    loadimpact: {
      name: 'T-INFRA-LOAD-01: Mixed Realistic - Electoral Pico',
      projectID: 3356,
    },
  },
};

/**
 * Observer action: Register acta (30% of traffic)
 */
function observerRegisterActa(vus) {
  const credentials = getObserverCredentials(vus);

  try {
    const token = login(credentials, {
      scenario: 'mixed-realistic',
      action: 'observer-register',
    });

    randomSleep(0.5, 1);

    const cargoId = getRandomCargoId();
    const candidatoCount = getCandidatoCountForCargo(cargoId);

    const acta = {
      jrvId: getRandomJrvId(),
      cargoId: cargoId,
      totalSufragantes: 400 + Math.floor(Math.random() * 150),
      votosBlancos: Math.floor(Math.random() * 20),
      votosNulos: Math.floor(Math.random() * 10),
      detalles: generateVotos(
        400 + Math.floor(Math.random() * 150),
        candidatoCount
      ),
    };

    registrarActa(token, acta, {
      scenario: 'mixed-realistic',
      action: 'observer-register',
    });

    randomSleep(1, 2);
  } catch (error) {
    console.error(
      `VU ${vus} - observer register error: ${error.message}`
    );
  }
}

/**
 * Coordinator action: Review actas (40% of traffic)
 */
function coordinatorReviewActas(vus) {
  const credentials = getCoordinatorCredentials(vus);

  try {
    const token = login(credentials, {
      scenario: 'mixed-realistic',
      action: 'coordinator-review',
    });

    randomSleep(0.5, 1);

    const states = ['ENVIADA', 'EN_PROCESO', 'COMPLETADA'];
    const state = states[Math.floor(Math.random() * states.length)];
    const page = Math.floor(Math.random() * 5);

    const listResponse = listarActas(
      token,
      {
        estado: state,
        page: page,
        size: 20,
      },
      {
        scenario: 'mixed-realistic',
        action: 'coordinator-review',
      }
    );

    if (listResponse && listResponse.content && listResponse.content.length > 0) {
      const actaId = listResponse.content[0].id;
      getActaDetail(token, actaId, {
        scenario: 'mixed-realistic',
        action: 'coordinator-review',
      });

      randomSleep(1, 3);
    }

    randomSleep(1, 2);
  } catch (error) {
    console.error(
      `VU ${vus} - coordinator review error: ${error.message}`
    );
  }
}

/**
 * Admin action: Check dashboard (20% of traffic)
 */
function adminCheckDashboard(vus) {
  const credentials = config.admin;

  try {
    const token = login(credentials, {
      scenario: 'mixed-realistic',
      action: 'admin-dashboard',
    });

    randomSleep(0.5, 1);

    getDashboard(token, {
      scenario: 'mixed-realistic',
      action: 'admin-dashboard',
    });

    // Admin waits longer before next refresh (30-60s in real scenario)
    randomSleep(2, 4);
  } catch (error) {
    console.error(`VU ${vus} - admin dashboard error: ${error.message}`);
  }
}

/**
 * Distributed traffic based on weighted random selection
 */
export default function __default() {
  logScenarioStart('T-INFRA-LOAD-01: Mixed Realistic - Electoral Pico');

  const rnd = Math.random();

  if (rnd < 0.3) {
    // 30% Observer registering actas
    observerRegisterActa(__VU);
  } else if (rnd < 0.7) {
    // 40% Coordinator reviewing actas
    coordinatorReviewActas(__VU);
  } else if (rnd < 0.9) {
    // 20% Admin checking dashboard
    adminCheckDashboard(__VU);
  } else {
    // 10% Misc (login, refresh, etc.)
    const credentials = getObserverCredentials(__VU);
    try {
      login(credentials, {
        scenario: 'mixed-realistic',
        action: 'misc-login',
      });
    } catch (error) {
      // Ignore errors in misc category
    }
  }
}
