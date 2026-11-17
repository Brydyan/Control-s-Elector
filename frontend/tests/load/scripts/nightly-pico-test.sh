#!/bin/bash

#######################################################################
# Nightly Pico Test Runner (0→150 VUs, 30 minutos)
# T-INFRA-LOAD-02: Full electoral load simulation
#
# Cron: 0 2 * * * /home/electoral/k6/scripts/nightly-pico-test.sh
#
# Purpose:
#   - Simula cierre de votación (17:00-17:30)
#   - 0→150 VUs con mixed traffic
#   - Ejecuta diariamente en horario fuera de pico
#   - Reporta resultados a Slack + email
#######################################################################

set -e

# ===== CONFIGURATION =====
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"
REPORTS_DIR="${SCRIPT_DIR}/reports"
LOGS_DIR="${SCRIPT_DIR}/logs"

API_HOST="${API_HOST:-localhost:8080}"
SLACK_WEBHOOK="${SLACK_WEBHOOK:-}"  # Set via env or .env file
EMAIL_TO="${EMAIL_TO:-ops@control.ttcs}"
EMAIL_FROM="${EMAIL_FROM:-k6-nightly@control.ttcs}"

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
DATETIME=$(date '+%Y-%m-%d %H:%M:%S')

# Create directories
mkdir -p "${RESULTS_DIR}" "${REPORTS_DIR}" "${LOGS_DIR}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ===== LOGGING FUNCTIONS =====

log_info() {
  echo -e "${BLUE}ℹ️  $1${NC}" | tee -a "${LOGS_DIR}/nightly-${TIMESTAMP}.log"
}

log_success() {
  echo -e "${GREEN}✅ $1${NC}" | tee -a "${LOGS_DIR}/nightly-${TIMESTAMP}.log"
}

log_error() {
  echo -e "${RED}❌ $1${NC}" | tee -a "${LOGS_DIR}/nightly-${TIMESTAMP}.log"
}

log_warning() {
  echo -e "${YELLOW}⚠️  $1${NC}" | tee -a "${LOGS_DIR}/nightly-${TIMESTAMP}.log"
}

# ===== HEALTH CHECKS =====

check_backend_health() {
  log_info "Checking backend health at ${API_HOST}..."

  if curl -sf "http://${API_HOST}/actuator/health" > /dev/null 2>&1; then
    log_success "Backend is healthy"
    return 0
  else
    log_error "Backend is not responding at ${API_HOST}"
    return 1
  fi
}

check_database_health() {
  log_info "Checking database connectivity..."

  if PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -U "${DB_USER}" -d "${DB_NAME}" \
    -c "SELECT 1" > /dev/null 2>&1; then
    log_success "Database is healthy"
    return 0
  else
    log_error "Database is not responding"
    return 1
  fi
}

# ===== SEED TEST DATA =====

seed_test_data() {
  log_info "Seeding test data..."

  SQL_FILE="/tmp/k6-seed-${TIMESTAMP}.sql"

  cat > "$SQL_FILE" << 'EOF'
-- Ensure test users exist
INSERT INTO usuario (email, nombre, apellido, password_hash, estado_usuario, rol)
VALUES
  ('obs1@electoral.ec', 'Observer', '1', '$2a$12$E8/48x.jSGHPG/eXOlGG.ORRz51OMhLHkTyQhgGGGXYlQQVMJNfR6', 'ACTIVO', 'OBSERVER'),
  ('obs2@electoral.ec', 'Observer', '2', '$2a$12$E8/48x.jSGHPG/eXOlGG.ORRz51OMhLHkTyQhgGGGXYlQQVMJNfR6', 'ACTIVO', 'OBSERVER'),
  ('obs3@electoral.ec', 'Observer', '3', '$2a$12$E8/48x.jSGHPG/eXOlGG.ORRz51OMhLHkTyQhgGGGXYlQQVMJNfR6', 'ACTIVO', 'OBSERVER'),
  ('obs4@electoral.ec', 'Observer', '4', '$2a$12$E8/48x.jSGHPG/eXOlGG.ORRz51OMhLHkTyQhgGGGXYlQQVMJNfR6', 'ACTIVO', 'OBSERVER'),
  ('obs5@electoral.ec', 'Observer', '5', '$2a$12$E8/48x.jSGHPG/eXOlGG.ORRz51OMhLHkTyQhgGGGXYlQQVMJNfR6', 'ACTIVO', 'OBSERVER'),
  ('coord1@electoral.ec', 'Coordinator', '1', '$2a$12$E8/48x.jSGHPG/eXOlGG.ORRz51OMhLHkTyQhgGGGXYlQQVMJNfR6', 'ACTIVO', 'COORDINATOR'),
  ('coord2@electoral.ec', 'Coordinator', '2', '$2a$12$E8/48x.jSGHPG/eXOlGG.ORRz51OMhLHkTyQhgGGGXYlQQVMJNfR6', 'ACTIVO', 'COORDINATOR'),
  ('admin@electoral.ec', 'Admin', 'Electoral', '$2a$12$E8/48x.jSGHPG/eXOlGG.ORRz51OMhLHkTyQhgGGGXYlQQVMJNfR6', 'ACTIVO', 'MASTER')
ON CONFLICT (email) DO NOTHING;

-- Verify count
SELECT COUNT(*) as test_users FROM usuario WHERE email LIKE '%@electoral.ec';
EOF

  PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -U "${DB_USER}" -d "${DB_NAME}" \
    -f "$SQL_FILE" > /dev/null

  log_success "Test data seeded"
  rm -f "$SQL_FILE"
}

# ===== RUN K6 PICO TEST =====

run_pico_test() {
  local output_file="pico-nightly-${TIMESTAMP}.json"
  local test_log="${LOGS_DIR}/pico-run-${TIMESTAMP}.log"

  log_info "Starting Pico test (0→150 VUs, ~30 min)..."
  echo "Test start time: ${DATETIME}" >> "$test_log"

  k6 run "${SCRIPT_DIR}/scenarios/mixed-realistic.js" \
    --out json="${RESULTS_DIR}/${output_file}" \
    -e API_HOST="${API_HOST}" \
    2>&1 | tee -a "$test_log"

  log_success "Pico test completed. Results: ${output_file}"
  echo "$output_file"
}

# ===== PARSE & VALIDATE RESULTS =====

parse_results() {
  local results_file="$1"

  if [ ! -f "$results_file" ]; then
    log_error "Results file not found: $results_file"
    return 1
  fi

  # Extract metrics using jq
  P95=$(jq -r '.metrics.http_req_duration.values.p95 // "N/A"' "$results_file")
  P99=$(jq -r '.metrics.http_req_duration.values.p99 // "N/A"' "$results_file")
  FAILURES=$(jq -r '.metrics.http_req_failed.values.rate // 0' "$results_file")
  TOTAL_REQS=$(jq -r '.metrics.http_reqs.values.count // 0' "$results_file")

  # Create report
  REPORT_FILE="${REPORTS_DIR}/pico-${TIMESTAMP}.txt"

  cat > "$REPORT_FILE" << EOF
═══════════════════════════════════════════════════════════
  K6 PICO TEST REPORT
═══════════════════════════════════════════════════════════
Date/Time:       ${DATETIME}
Test Type:       Pico Electoral (0→150 VUs, 30 min)
Backend:         ${API_HOST}
Results File:    ${results_file}

METRICS
───────────────────────────────────────────────────────────
p95 Latency:     ${P95}ms (threshold: 5000ms)
p99 Latency:     ${P99}ms (threshold: 8000ms)
Failure Rate:    ${FAILURES}% (threshold: <10%)
Total Requests:  ${TOTAL_REQS}

VALIDATION
───────────────────────────────────────────────────────────
EOF

  # Validate thresholds
  PASS_P95=0
  PASS_P99=0
  PASS_FAILURES=0

  if [ "$P95" != "N/A" ] && [ "${P95%.*}" -le 5000 ]; then
    echo "✅ p95 PASS" >> "$REPORT_FILE"
    PASS_P95=1
  else
    echo "❌ p95 FAIL (${P95}ms > 5000ms)" >> "$REPORT_FILE"
  fi

  if [ "$P99" != "N/A" ] && [ "${P99%.*}" -le 8000 ]; then
    echo "✅ p99 PASS" >> "$REPORT_FILE"
    PASS_P99=1
  else
    echo "❌ p99 FAIL (${P99}ms > 8000ms)" >> "$REPORT_FILE"
  fi

  if [ "${FAILURES%.*}" -lt 10 ]; then
    echo "✅ Failures PASS" >> "$REPORT_FILE"
    PASS_FAILURES=1
  else
    echo "❌ Failures FAIL (${FAILURES}% > 10%)" >> "$REPORT_FILE"
  fi

  echo "" >> "$REPORT_FILE"

  # Overall status
  if [ "$PASS_P95" -eq 1 ] && [ "$PASS_P99" -eq 1 ] && [ "$PASS_FAILURES" -eq 1 ]; then
    echo "OVERALL STATUS: ✅ PASS" >> "$REPORT_FILE"
    return 0
  else
    echo "OVERALL STATUS: ❌ FAIL" >> "$REPORT_FILE"
    return 1
  fi
}

# ===== NOTIFICATIONS =====

notify_slack() {
  local report_file="$1"
  local status="$2"  # "PASS" or "FAIL"

  if [ -z "$SLACK_WEBHOOK" ]; then
    log_warning "SLACK_WEBHOOK not set, skipping Slack notification"
    return
  fi

  local color="good"
  local emoji="✅"

  if [ "$status" = "FAIL" ]; then
    color="danger"
    emoji="❌"
  fi

  local message=$(cat "$report_file" | sed 's/"/\\"/g')

  curl -X POST "$SLACK_WEBHOOK" \
    -H 'Content-Type: application/json' \
    -d "{
      \"attachments\": [
        {
          \"color\": \"$color\",
          \"title\": \"$emoji K6 Pico Test - $status\",
          \"text\": \"$(cat "$report_file")\",
          \"footer\": \"Nightly Load Test\",
          \"ts\": $(date +%s)
        }
      ]
    }" 2>/dev/null

  log_success "Slack notification sent"
}

notify_email() {
  local report_file="$1"
  local status="$2"

  if ! command -v mail &> /dev/null && ! command -v sendmail &> /dev/null; then
    log_warning "Mail command not available, skipping email"
    return
  fi

  local subject="K6 Pico Test - ${status} - $(date '+%Y-%m-%d %H:%M')"

  cat "$report_file" | mail -s "$subject" -r "$EMAIL_FROM" "$EMAIL_TO"

  log_success "Email notification sent to $EMAIL_TO"
}

# ===== CLEANUP OLD RESULTS =====

cleanup_old_results() {
  log_info "Cleaning up results older than 30 days..."

  find "${RESULTS_DIR}" -name "*.json" -mtime +30 -delete
  find "${LOGS_DIR}" -name "*.log" -mtime +30 -delete
  find "${REPORTS_DIR}" -name "*.txt" -mtime +30 -delete

  log_success "Cleanup complete"
}

# ===== MAIN FLOW =====

main() {
  log_info "═════════════════════════════════════════════════════════"
  log_info "K6 NIGHTLY PICO TEST — $(date '+%Y-%m-%d %H:%M:%S')"
  log_info "═════════════════════════════════════════════════════════"
  log_info ""

  # Load env file if exists
  if [ -f "${SCRIPT_DIR}/.env" ]; then
    # shellcheck disable=SC1090
    source "${SCRIPT_DIR}/.env"
  fi

  # Pre-flight checks
  if ! check_backend_health; then
    log_error "Backend not healthy, aborting"
    exit 1
  fi

  if ! check_database_health; then
    log_error "Database not healthy, aborting"
    exit 1
  fi

  # Seed data
  seed_test_data

  # Run test
  if ! OUTPUT_FILE=$(run_pico_test); then
    log_error "K6 test execution failed"
    exit 1
  fi

  FULL_OUTPUT_PATH="${RESULTS_DIR}/${OUTPUT_FILE}"

  # Parse results
  if parse_results "$FULL_OUTPUT_PATH"; then
    log_success "Test PASSED — All SLAs met"
    TEST_STATUS="PASS"
  else
    log_error "Test FAILED — SLAs exceeded"
    TEST_STATUS="FAIL"
  fi

  # Get latest report
  LATEST_REPORT=$(ls -t "${REPORTS_DIR}"/pico-*.txt | head -1)

  # Notify
  notify_slack "$LATEST_REPORT" "$TEST_STATUS"
  notify_email "$LATEST_REPORT" "$TEST_STATUS"

  # Cleanup
  cleanup_old_results

  log_info ""
  log_info "═════════════════════════════════════════════════════════"
  log_info "Nightly pico test completed — ${TEST_STATUS}"
  log_info "Report: $LATEST_REPORT"
  log_info "═════════════════════════════════════════════════════════"

  # Exit code
  [ "$TEST_STATUS" = "PASS" ] && exit 0 || exit 1
}

# Execute
main "$@"
