/**
 * Coordinator Workflow: Login → Listar Actas → Ver Detalle
 * T-INFRA-LOAD-01: Scenario 3 (CRITICAL)
 *
 * Purpose: Simulate coordinators reviewing submitted actas
 * - Complex queries with filters, pagination, aggregations
 * - Frequent dashboard refreshes during pico
 * - 4 database queries per list request
 *
 * Run: k6 run scenarios/listar-actas.js --vus 20 --duration 20m
 */

import { sleep } from 'k6';
import { config, getCoordinatorCredentials } from '../config.js';
import {
  login,
  listarActas,
  getActaDetail,
  buildThresholds,
  randomSleep,
  logScenarioStart,
} from '../common.js';

export const options = {
  vus: 20, // 20 virtual users (20 coordinators)
  duration: '20m', // 20 minutes

  thresholds: buildThresholds('rampUp'),

  ext: {
    loadimpact: {
      name: 'T-INFRA-LOAD-01: Listar Actas - Coordinator Dashboard',
      projectID: 3356,
    },
  },
};

export default function () {
  logScenarioStart(
    'T-INFRA-LOAD-01: Listar Actas - Coordinator Dashboard'
  );

  const credentials = getCoordinatorCredentials(__VU);

  try {
    // Step 1: Login
    const token = login(credentials, {
      scenario: 'listar-actas',
      step: '1-login',
    });

    // Step 2: Simulate coordinator monitoring dashboard
    // In pico, coordinators refresh every 30-60 seconds to monitor progress

    // Filter options to simulate real usage
    const filterStates = [
      'ENVIADA',
      'EN_PROCESO',
      'COMPLETADA',
      'EN_DISPUTA',
    ];
    const randomState =
      filterStates[Math.floor(Math.random() * filterStates.length)];

    // Listar with pagination
    const page = Math.floor(Math.random() * 5); // Pages 0-4
    const listResponse = listarActas(
      token,
      {
        estado: randomState,
        page: page,
        size: 20,
        sort: 'createdAt',
        dir: 'DESC',
      },
      {
        scenario: 'listar-actas',
        step: '2-listar',
        estado: randomState,
        page: page,
      }
    );

    if (!listResponse) {
      console.error(`VU ${__VU}: Failed to listar actas`);
      return;
    }

    randomSleep(1, 2);

    // Step 3: Open detail of first acta (if available)
    const actas = listResponse.content;
    if (actas && actas.length > 0) {
      const firstActaId = actas[0].id;

      // Get detail
      const detailResponse = getActaDetail(token, firstActaId, {
        scenario: 'listar-actas',
        step: '3-detalle',
        actaId: firstActaId,
      });

      if (!detailResponse) {
        console.error(
          `VU ${__VU}: Failed to get acta detail for ID ${firstActaId}`
        );
      }

      randomSleep(1, 3); // Coordinator reads the detail
    }

    // Step 4: Simulate coordinator waiting before next refresh
    // In reality, coordinators refresh dashboard every 30-60s during pico
    randomSleep(2, 4);
  } catch (error) {
    console.error(
      `VU ${__VU} error in listar-actas: ${error.message}`
    );
  }
}
