#!/bin/bash

#######################################################################
# Weekly Stress Test Runner (200 VUs sustained, 30 minutos)
# T-INFRA-LOAD-03: Find breaking point
#
# Cron: 0 3 * * 0 /home/electoral/k6/scripts/weekly-stress-test.sh
#       ^ 3 AM UTC, Sundays
#
# Purpose:
#   - Find system breaking point
#   - Identify max sustainable load
#   - Detect resource leaks
#   - Trend analysis week-over-week
#######################################################################

set -e

# ===== CONFIGURATION =====
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"
REPORTS_DIR="${SCRIPT_DIR}/reports"
LOGS_DIR="${SCRIPT_DIR}/logs"

API_HOST="${API_HOST:-localhost:8080}"
SLACK_WEBHOOK="${SLACK_WEBHOOK:-}"
EMAIL_TO="${EMAIL_TO:-ops@control.ttcs}"

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
DATETIME=$(date '+%Y-%m-%d %H:%M:%S')

# Create directories
mkdir -p "${RESULTS_DIR}" "${REPORTS_DIR}" "${LOGS_DIR}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ===== LOGGING =====

log_info() {
  echo -e "${BLUE}ℹ️  $1${NC}" | tee -a "${LOGS_DIR}/weekly-stress-${TIMESTAMP}.log"
}

log_success() {
  echo -e "${GREEN}✅ $1${NC}" | tee -a "${LOGS_DIR}/weekly-stress-${TIMESTAMP}.log"
}

log_error() {
  echo -e "${RED}❌ $1${NC}" | tee -a "${LOGS_DIR}/weekly-stress-${TIMESTAMP}.log"
}

log_warning() {
  echo -e "${YELLOW}⚠️  $1${NC}" | tee -a "${LOGS_DIR}/weekly-stress-${TIMESTAMP}.log"
}

# ===== HEALTH CHECKS =====

check_backend_health() {
  log_info "Checking backend health..."

  if curl -sf "http://${API_HOST}/actuator/health" > /dev/null 2>&1; then
    log_success "Backend healthy"
    return 0
  else
    log_error "Backend not responding"
    return 1
  fi
}

check_database_health() {
  log_info "Checking database..."

  if PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -U "${DB_USER}" -d "${DB_NAME}" \
    -c "SELECT 1" > /dev/null 2>&1; then
    log_success "Database healthy"
    return 0
  else
    log_error "Database not responding"
    return 1
  fi
}

# ===== RUN STRESS TEST =====

run_stress_test() {
  local output_file="stress-weekly-${TIMESTAMP}.json"
  local test_log="${LOGS_DIR}/stress-run-${TIMESTAMP}.log"

  log_info "Starting Stress test (200 VUs sustained, 30 min)..."
  echo "Test start: ${DATETIME}" >> "$test_log"

  k6 run "${SCRIPT_DIR}/scenarios/stress.js" \
    --vus 200 \
    --duration 30m \
    --out json="${RESULTS_DIR}/${output_file}" \
    -e API_HOST="${API_HOST}" \
    2>&1 | tee -a "$test_log"

  log_success "Stress test completed"
  echo "$output_file"
}

# ===== ANALYZE RESULTS =====

analyze_stress_results() {
  local results_file="$1"

  if [ ! -f "$results_file" ]; then
    log_error "Results file not found"
    return 1
  fi

  # Extract metrics
  P95=$(jq -r '.metrics.http_req_duration.values.p95 // "N/A"' "$results_file")
  P99=$(jq -r '.metrics.http_req_duration.values.p99 // "N/A"' "$results_file")
  FAILURES=$(jq -r '.metrics.http_req_failed.values.rate // 0' "$results_file")
  TOTAL_REQS=$(jq -r '.metrics.http_reqs.values.count // 0' "$results_file")
  RPS=$(jq -r '.metrics.http_reqs.values.rate // 0' "$results_file")

  # Get resource usage if available
  MEMORY_PEAK=$(jq -r '.metrics | keys[] | select(. | contains("memory")) | . as $key | .[$key].values.max // "N/A"' "$results_file" 2>/dev/null || echo "N/A")

  # Create detailed report
  REPORT_FILE="${REPORTS_DIR}/stress-${TIMESTAMP}.txt"

  cat > "$REPORT_FILE" << EOF
═══════════════════════════════════════════════════════════
  K6 WEEKLY STRESS TEST REPORT
═══════════════════════════════════════════════════════════
Date/Time:       ${DATETIME}
Test Type:       Stress Test (200 VUs sustained, 30 min)
Backend:         ${API_HOST}
Results File:    ${results_file}

PERFORMANCE METRICS
───────────────────────────────────────────────────────────
p95 Latency:     ${P95}ms
p99 Latency:     ${P99}ms
Failure Rate:    ${FAILURES}%
Total Requests:  ${TOTAL_REQS}
Requests/sec:    ${RPS}

INTERPRETATION
───────────────────────────────────────────────────────────
EOF

  # Classify breaking point
  if [ "$FAILURES" -gt 50 ]; then
    cat >> "$REPORT_FILE" << 'EOF'
Status: ❌ BREAKING POINT EXCEEDED
  - Failure rate > 50%
  - System is unable to handle 200 VUs
  - Action: Review capacity planning

Recommendations:
  1. Scale backend (add replicas)
  2. Optimize database queries
  3. Implement caching
  4. Review connection pools

EOF
  elif [ "$FAILURES" -gt 10 ]; then
    cat >> "$REPORT_FILE" << 'EOF'
Status: ⚠️  NEAR BREAKING POINT
  - Failure rate 10-50%
  - System degrading under 200 VUs
  - Action: Monitor, prepare scaling

Recommendations:
  1. Monitor metrics closely during elections
  2. Have scaling procedures ready
  3. Optimize slow queries
  4. Load test 150 VUs regularly

EOF
  else
    cat >> "$REPORT_FILE" << 'EOF'
Status: ✅ SUSTAINABLE
  - System handling 200 VUs reasonably
  - Failures < 10%
  - Action: Continue monitoring

Notes:
  - System appears stable at 200 VUs
  - 150 VUs (realistic pico) well below limit
  - Good headroom for peak elections

EOF
  fi

  echo "" >> "$REPORT_FILE"
  echo "TECHNICAL ANALYSIS" >> "$REPORT_FILE"
  echo "───────────────────────────────────────────────────────────" >> "$REPORT_FILE"
  echo "p95 latency: $([ "${P95%.*}" -gt 10000 ] && echo "⚠️  HIGH (${P95}ms)" || echo "✅ OK (${P95}ms)")" >> "$REPORT_FILE"
  echo "p99 latency: $([ "${P99%.*}" -gt 15000 ] && echo "⚠️  HIGH (${P99}ms)" || echo "✅ OK (${P99}ms)")" >> "$REPORT_FILE"
  echo "Memory peak: ${MEMORY_PEAK}" >> "$REPORT_FILE"
  echo "" >> "$REPORT_FILE"

  cat "$REPORT_FILE"
}

# ===== TREND ANALYSIS =====

analyze_trends() {
  log_info "Analyzing stress test trends..."

  TRENDS_FILE="${REPORTS_DIR}/stress-trends-$(date +%Y-%m).txt"

  cat > "$TRENDS_FILE" << EOF
═══════════════════════════════════════════════════════════
  STRESS TEST TRENDS — $(date '+%B %Y')
═══════════════════════════════════════════════════════════

EOF

  # List last 4 weeks of stress tests
  ls -t "${REPORTS_DIR}"/stress-*.txt | head -4 | while read -r report; do
    date=$(basename "$report" | sed 's/stress-//;s/.txt//')
    p95=$(grep "p95 Latency:" "$report" | awk '{print $NF}')
    failures=$(grep "Failure Rate:" "$report" | awk '{print $NF}')

    echo "$(date -d "@${date:0:8}" '+%Y-%m-%d'): p95=${p95}, failures=${failures}" >> "$TRENDS_FILE"
  done

  cat >> "$TRENDS_FILE" << 'EOF'

TREND INTERPRETATION
───────────────────────────────────────────────────────────
- If p95 increasing week-over-week → degradation, investigate
- If failures increasing → capacity limit approaching
- If stable → system is healthy, no action needed

EOF

  log_success "Trends saved to $TRENDS_FILE"
}

# ===== NOTIFICATIONS =====

notify_slack() {
  local report_file="$1"

  if [ -z "$SLACK_WEBHOOK" ]; then
    log_warning "SLACK_WEBHOOK not set, skipping Slack"
    return
  fi

  local failures=$(grep "Failure Rate:" "$report_file" | awk '{print $NF}' | sed 's/%//')
  local color="good"

  if (( $(echo "$failures > 10" | bc -l) )); then
    color="warning"
  fi

  if (( $(echo "$failures > 50" | bc -l) )); then
    color="danger"
  fi

  curl -X POST "$SLACK_WEBHOOK" \
    -H 'Content-Type: application/json' \
    -d "{
      \"attachments\": [
        {
          \"color\": \"$color\",
          \"title\": \"Weekly Stress Test Complete\",
          \"text\": \"$(cat "$report_file" | sed 's/"/\\"/g' | head -20)\",
          \"footer\": \"K6 Stress Test\",
          \"ts\": $(date +%s)
        }
      ]
    }" 2>/dev/null

  log_success "Slack notification sent"
}

notify_email() {
  local report_file="$1"

  if ! command -v mail &> /dev/null; then
    log_warning "Mail command unavailable"
    return
  fi

  mail -s "K6 Weekly Stress Test - $(date '+%Y-%m-%d')" \
    -r "k6-stress@control.ttcs" \
    "$EMAIL_TO" < "$report_file"

  log_success "Email sent to $EMAIL_TO"
}

# ===== MAIN =====

main() {
  log_info "═════════════════════════════════════════════════════════"
  log_info "K6 WEEKLY STRESS TEST — $(date '+%Y-%m-%d %H:%M:%S')"
  log_info "═════════════════════════════════════════════════════════"
  log_info ""

  # Load env
  if [ -f "${SCRIPT_DIR}/.env" ]; then
    # shellcheck disable=SC1090
    source "${SCRIPT_DIR}/.env"
  fi

  # Pre-checks
  if ! check_backend_health; then
    log_error "Backend unhealthy, aborting"
    exit 1
  fi

  if ! check_database_health; then
    log_error "Database unhealthy, aborting"
    exit 1
  fi

  # Run test
  if ! OUTPUT_FILE=$(run_stress_test); then
    log_error "Stress test failed"
    exit 1
  fi

  FULL_OUTPUT_PATH="${RESULTS_DIR}/${OUTPUT_FILE}"

  # Analyze
  analyze_stress_results "$FULL_OUTPUT_PATH"

  # Trends
  analyze_trends

  # Get latest report
  LATEST_REPORT=$(ls -t "${REPORTS_DIR}"/stress-*.txt | head -1)

  # Notify
  notify_slack "$LATEST_REPORT"
  notify_email "$LATEST_REPORT"

  log_info ""
  log_info "═════════════════════════════════════════════════════════"
  log_info "Weekly stress test completed"
  log_info "Report: $LATEST_REPORT"
  log_info "═════════════════════════════════════════════════════════"

  exit 0
}

# Execute
main "$@"
