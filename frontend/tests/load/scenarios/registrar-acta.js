/**
 * Observer Workflow: Login → Registrar Acta
 * T-INFRA-LOAD-01: Scenario 2 (CRITICAL)
 *
 * Purpose: Simulate observer registering election results
 * - Most critical endpoint under pico load
 * - 5 database queries per request (includes locks)
 * - Longest latency of all endpoints
 *
 * Run: k6 run scenarios/registrar-acta.js --vus 30 --duration 20m
 */

import { sleep } from 'k6';
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
  vus: 30, // 30 virtual users (30 observers registering actas simultaneously)
  duration: '20m', // 20 minutes

  thresholds: buildThresholds('rampUp'),

  ext: {
    loadimpact: {
      name: 'T-INFRA-LOAD-01: Registrar Acta - Observer Workflow',
      projectID: 3356,
    },
  },
};

export default function () {
  logScenarioStart(
    'T-INFRA-LOAD-01: Registrar Acta - Observer Workflow'
  );

  const credentials = getObserverCredentials(__VU);

  try {
    // Step 1: Login
    const token = login(credentials, {
      scenario: 'registrar-acta',
      step: '1-login',
    });

    // Step 2: Simulate observer working in the field
    randomSleep(1, 2);

    // Step 3: Registrar Acta (critical endpoint)
    const cargoId = getRandomCargoId();
    const candidatoCount = getCandidatoCountForCargo(cargoId);

    const acta = {
      jrvId: getRandomJrvId(),
      cargoId: cargoId,
      totalSufragantes: 400 + Math.floor(Math.random() * 150), // 400-550 voters
      votosBlancos: Math.floor(Math.random() * 20),
      votosNulos: Math.floor(Math.random() * 10),
      detalles: generateVotos(
        400 + Math.floor(Math.random() * 150),
        candidatoCount
      ),
    };

    const result = registrarActa(token, acta, {
      scenario: 'registrar-acta',
      step: '3-registrar',
      cargoId: cargoId,
      candidatoCount: candidatoCount,
    });

    if (!result) {
      console.error(`VU ${__VU}: Failed to registrar acta`);
    }

    // Step 4: Simulate user thinking time before next action
    randomSleep(2, 4);
  } catch (error) {
    console.error(`VU ${__VU} error in registrar-acta: ${error.message}`);
  }
}
