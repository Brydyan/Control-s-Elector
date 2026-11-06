/**
 * Baseline Load Test: Login Only
 * T-INFRA-LOAD-01: Scenario 1
 *
 * Purpose: Verify system is healthy under minimal load
 * - Measure login performance
 * - Identify authentication bottlenecks
 * - Baseline for comparison with complex scenarios
 *
 * Run: k6 run scenarios/login.js --vus 10 --duration 5m
 */

import { sleep } from 'k6';
import {
  config,
  getObserverCredentials,
} from '../config.js';
import {
  login,
  buildThresholds,
  logScenarioStart,
} from '../common.js';

export const options = {
  vus: 10, // 10 virtual users
  duration: '5m', // 5 minutes

  thresholds: buildThresholds('baseline'),

  ext: {
    loadimpact: {
      name: 'T-INFRA-LOAD-01: Baseline - Login Only',
      projectID: 3356,
    },
  },
};

export default function () {
  logScenarioStart('T-INFRA-LOAD-01: Baseline - Login Only');

  // Each VU gets assigned credentials
  const credentials = getObserverCredentials(__VU);

  // Attempt login
  try {
    login(credentials, {
      scenario: 'baseline',
    });

    // Simulate user waiting before next action
    sleep(__ENV.SLEEP_DURATION ? parseInt(__ENV.SLEEP_DURATION) : 2);
  } catch (error) {
    console.error(`VU ${__VU} failed: ${error.message}`);
  }
}
