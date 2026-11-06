#!/bin/bash

#######################################################################
# K6 Load Testing Runner
# T-INFRA-LOAD-01: Control Electoral - Load Testing Script
#
# Usage:
#   ./run.sh baseline         # Run baseline test (10 VUs, 5 min)
#   ./run.sh rampup           # Run ramp-up test (0→100 VUs)
#   ./run.sh pico             # Run pico test (0→150 VUs, most realistic)
#   ./run.sh stress           # Run stress test (200 VUs, breaking point)
#   ./run.sh mixed            # Alias for pico (most commonly used)
#   ./run.sh all              # Run all tests sequentially
#   ./run.sh help             # Show this help message
#
# Environment Variables:
#   API_HOST              Backend URL (default: localhost:8080)
#   SLEEP_DURATION        Sleep between requests in seconds (default: 2)
#   K6_DOCKER             Use Docker instead of local k6 (default: false)
#######################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"
API_HOST="${API_HOST:-localhost:8080}"
SLEEP_DURATION="${SLEEP_DURATION:-2}"
K6_DOCKER="${K6_DOCKER:-false}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Create results directory if it doesn't exist
mkdir -p "${RESULTS_DIR}"

# Determine k6 command
if [ "$K6_DOCKER" = "true" ]; then
  K6_CMD="docker run --rm -v ${SCRIPT_DIR}:/workspace grafana/k6:latest"
else
  K6_CMD="k6"
fi

# Check if k6 is available
if ! command -v $K6_CMD &> /dev/null && [ "$K6_DOCKER" = "false" ]; then
  echo -e "${RED}❌ k6 not found. Install with: npm install -g k6${NC}"
  echo -e "${BLUE}Or set K6_DOCKER=true to use Docker${NC}"
  exit 1
fi

#######################################################################
# Helper Functions
#######################################################################

log_info() {
  echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
  echo -e "${GREEN}✅ $1${NC}"
}

log_error() {
  echo -e "${RED}❌ $1${NC}"
}

log_warning() {
  echo -e "${YELLOW}⚠️  $1${NC}"
}

log_test_start() {
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo -e "${BLUE}▶ Iniciando: $1${NC}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo ""
}

log_test_end() {
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo -e "${GREEN}✓ Completado: $1${NC}"
  echo -e "${BLUE}Resultados: ${RESULTS_DIR}/$2${NC}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo ""
}

#######################################################################
# Test Scenarios
#######################################################################

test_baseline() {
  local test_name="Baseline - Login Only (10 VUs, 5 min)"
  local output_file="baseline-${TIMESTAMP}.json"

  log_test_start "$test_name"

  $K6_CMD run "${SCRIPT_DIR}/scenarios/login.js" \
    --vus 10 \
    --duration 5m \
    --out json="${RESULTS_DIR}/${output_file}" \
    -e API_HOST="${API_HOST}" \
    -e SLEEP_DURATION="${SLEEP_DURATION}"

  log_test_end "$test_name" "$output_file"
}

test_rampup() {
  local test_name="Ramp-up Test (0→100 VUs over 25 min)"
  local output_file="ramp-up-${TIMESTAMP}.json"

  log_test_start "$test_name"

  $K6_CMD run "${SCRIPT_DIR}/scenarios/registrar-acta.js" \
    --stage 2m:20vus \
    --stage 5m:50vus \
    --stage 8m:100vus \
    --stage 5m:0vus \
    --out json="${RESULTS_DIR}/${output_file}" \
    -e API_HOST="${API_HOST}" \
    -e SLEEP_DURATION="${SLEEP_DURATION}"

  log_test_end "$test_name" "$output_file"
}

test_pico() {
  local test_name="Pico Electoral - Mixed Traffic (0→150 VUs, 30 min)"
  local output_file="pico-${TIMESTAMP}.json"

  log_test_start "$test_name"
  log_info "Escenario realista: 30% observers registrando, 40% coordinadores revisando, 20% admin dashboard, 10% misc"
  log_info "Duración: 2m ramp-up + 10m ramp-to-pico + 10m peak + 5m ramp-down + 3m cool-off = 30m total"

  $K6_CMD run "${SCRIPT_DIR}/scenarios/mixed-realistic.js" \
    --out json="${RESULTS_DIR}/${output_file}" \
    -e API_HOST="${API_HOST}" \
    -e SLEEP_DURATION="${SLEEP_DURATION}"

  log_test_end "$test_name" "$output_file"
}

test_stress() {
  local test_name="Stress Test - Breaking Point (200 VUs, 30 min)"
  local output_file="stress-${TIMESTAMP}.json"

  log_test_start "$test_name"
  log_warning "Escenario UNREALISTA: push system beyond limits"
  log_warning "Objetivo: identificar breaking point, no como acceptance criteria"

  $K6_CMD run "${SCRIPT_DIR}/scenarios/stress-test.js" \
    --out json="${RESULTS_DIR}/${output_file}" \
    -e API_HOST="${API_HOST}" \
    -e SLEEP_DURATION="${SLEEP_DURATION}"

  log_test_end "$test_name" "$output_file"
}

test_listar_actas() {
  local test_name="Listar Actas - Coordinator Dashboard (20 VUs, 20 min)"
  local output_file="listar-actas-${TIMESTAMP}.json"

  log_test_start "$test_name"

  $K6_CMD run "${SCRIPT_DIR}/scenarios/listar-actas.js" \
    --out json="${RESULTS_DIR}/${output_file}" \
    -e API_HOST="${API_HOST}" \
    -e SLEEP_DURATION="${SLEEP_DURATION}"

  log_test_end "$test_name" "$output_file"
}

test_registrar_acta() {
  local test_name="Registrar Acta - Observer Workflow (30 VUs, 20 min)"
  local output_file="registrar-acta-${TIMESTAMP}.json"

  log_test_start "$test_name"

  $K6_CMD run "${SCRIPT_DIR}/scenarios/registrar-acta.js" \
    --out json="${RESULTS_DIR}/${output_file}" \
    -e API_HOST="${API_HOST}" \
    -e SLEEP_DURATION="${SLEEP_DURATION}"

  log_test_end "$test_name" "$output_file"
}

run_all_tests() {
  log_info "Ejecutando TODOS los tests en secuencia..."
  log_info "Tiempo estimado: ~2.5 horas"

  test_baseline
  test_rampup
  test_listar_actas
  test_registrar_acta
  test_pico
  test_stress

  log_success "Todos los tests completados!"
  log_info "Revisar resultados en: ${RESULTS_DIR}"
}

show_help() {
  cat << EOF

${BLUE}═══════════════════════════════════════════════════════════════${NC}
${BLUE}K6 Load Testing for Control Electoral${NC}
${BLUE}T-INFRA-LOAD-01: Infrastructure Load Testing${NC}
${BLUE}═══════════════════════════════════════════════════════════════${NC}

${GREEN}Uso:${NC}
  $0 [COMANDO] [OPCIONES]

${GREEN}Comandos:${NC}
  baseline              Test básico: Login solo (10 VUs, 5 min)
  rampup                Ramp-up test: 0→100 VUs (25 min)
  pico                  Pico electoral: 0→150 VUs (30 min) ⭐ MÁS REALISTA
  mixed                 Alias para 'pico'
  stress                Stress test: 200 VUs, find breaking point (30 min)
  listar-actas          Coordinator dashboard: 20 VUs (20 min)
  registrar-acta        Observer workflow: 30 VUs (20 min)
  all                   Ejecutar TODOS los tests en secuencia (~2.5h)
  help                  Mostrar esta ayuda

${GREEN}Variables de Entorno:${NC}
  API_HOST              URL del backend (default: localhost:8080)
  SLEEP_DURATION        Sleep entre requests en segundos (default: 2)
  K6_DOCKER             Usar Docker en lugar de k6 local (default: false)

${GREEN}Ejemplos:${NC}
  # Test básico contra localhost
  $0 baseline

  # Pico realista contra production
  API_HOST=api.electoral.ec:8080 $0 pico

  # Con Docker
  K6_DOCKER=true $0 stress

  # Todos los tests
  $0 all

${GREEN}Resultados:${NC}
  Guardan en: ${RESULTS_DIR}/
  Formato: JSON (para análisis posterior con k6 dashboard o herramientas custom)

${GREEN}Umbrales SLA:${NC}
  Login:           p95 < 1.5s,  p99 < 2s
  Registrar Acta:  p95 < 5s,    p99 < 8s
  Listar Actas:    p95 < 3s,    p99 < 5s
  Dashboard:       p95 < 3s,    p99 < 5s

${BLUE}═══════════════════════════════════════════════════════════════${NC}

EOF
}

#######################################################################
# Main
#######################################################################

main() {
  local command="${1:-help}"

  log_info "API_HOST: ${API_HOST}"
  log_info "Resultados: ${RESULTS_DIR}"
  log_info "Timestamp: ${TIMESTAMP}"
  echo ""

  case "$command" in
    baseline)
      test_baseline
      ;;
    rampup)
      test_rampup
      ;;
    pico | mixed)
      test_pico
      ;;
    stress)
      test_stress
      ;;
    listar-actas)
      test_listar_actas
      ;;
    registrar-acta)
      test_registrar_acta
      ;;
    all)
      run_all_tests
      ;;
    help | -h | --help)
      show_help
      ;;
    *)
      log_error "Comando desconocido: $command"
      echo ""
      show_help
      exit 1
      ;;
  esac
}

# Run main
main "$@"
