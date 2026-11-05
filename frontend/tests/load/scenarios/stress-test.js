/**
 * Stress Test: Find Breaking Point
 * T-INFRA-LOAD-01: Scenario 5 (For reference - UNREALISTIC)
 *
 * Purpose: Identify system breaking point
 * - Push system beyond expected limits
 * - Find degradation curve
 * - Understand capacity ceiling
 *
 * WARNING: This scenario is UNREALISTIC (200 VUs simultaneous)
 * Use only for understanding limits, not as acceptance criteria.
 *
 * Expected breaking point: 120-130 VUs (then failures > 10%)
 *
 * Run: k6 run scenarios/stress-test.js
 */

import {
  config,
  getObserverCredentials,
  getRandomJrvId,
  getRandomCargoId,
  getCandidatoCountForCargo,
  generateVotos,
} from '../config.js';
import {
  login,
  registrarActa,
  buildThresholds,
  randomSleep,
  logScenarioStart,
} from '../common.js';

export const options = {
  vus: 200, // 200 virtual users - UNREALISTIC but stress test
  duration: '30m', // Sustained for 30 minutes to observe degradation

  thresholds: buildThresholds('stress'),

  // More relaxed thresholds for stress test
  // We're looking for breaking point, not performance
  ext: {
    loadimpact: {
      name: 'T-INFRA-LOAD-01: Stress Test - Breaking Point',
      projectID: 3356,
    },
  },
};

export default function () {
  logScenarioStart('T-INFRA-LOAD-01: Stress Test - Breaking Point');

  const credentials = getObserverCredentials(__VU);

  try {
    // Step 1: Login
    const token = login(credentials, {
      scenario: 'stress-test',
      step: '1-login',
    });

    randomSleep(0.5, 1);

    // Step 2: Registrar Acta (most resource-intensive action)
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
      scenario: 'stress-test',
      step: '2-registrar',
    });

    randomSleep(0.5, 1);
  } catch (error) {
    console.error(`VU ${__VU} stress test error: ${error.message}`);
  }
}
