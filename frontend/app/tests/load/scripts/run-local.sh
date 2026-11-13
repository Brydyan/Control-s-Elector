#!/bin/bash
# ============================================================================
# K6 Load Test — Complete Electoral Flow (Local Execution)
# ============================================================================
#
# Ejecución del scenario completo en ambiente local:
# - 14 OBSERVERs suben 20 actas c/u (280 total)
# - 5 COORDINATORs validan/rechazan actas
# - 1 ADMIN ve dashboard
#
# Requisitos previos:
# 1. Backend running: mvn quarkus:dev (o docker)
# 2. Frontend running: pnpm start (o ng serve)
# 3. DB seeded: crear usuarios de test (obs1-5, coord1-2, admin)
# 4. K6 installed: https://grafana.com/docs/k6/latest/get-started/installation/
# 5. Fotos en: documents/pictures/acta-sample-*.jpg
#
# Uso:
#   chmod +x scripts/run-local.sh
#   ./scripts/run-local.sh               # Con valores por defecto
#   ./scripts/run-local.sh --vus 5 --duration 3m  # Parámetros custom
#   ./scripts/run-local.sh --api localhost:8080   # API custom
#
# ============================================================================

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Valores por defecto
API_HOST=${API_HOST:-"localhost:8080"}
VUS=${VUS:-20}
DURATION=${DURATION:-"10m"}
RESULTS_DIR="results"

# Parsear argumentos
while [[ $# -gt 0 ]]; do
  case $1 in
    --api)
      API_HOST="$2"
      shift 2
      ;;
    --vus)
      VUS="$2"
      shift 2
      ;;
    --duration)
      DURATION="$2"
      shift 2
      ;;
    --help)
      cat << 'EOF'
Uso: ./scripts/run-local.sh [opciones]

Opciones:
  --api HOST:PORT         API backend (default: localhost:8080)
  --vus N                 Virtual users (default: 20)
  --duration TIME         Duración (default: 10m, ej: 2m, 5m, 10m)
  --help                  Mostrar esta ayuda

Ejemplos:
  ./scripts/run-local.sh
  ./scripts/run-local.sh --vus 14 --duration 10m
  ./scripts/run-local.sh --api localhost:8080 --vus 20 --duration 5m

EOF
      exit 0
      ;;
    *)
      echo "Opción desconocida: $1"
      exit 1
      ;;
  esac
done

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                   K6 ELECTORAL LOAD TEST                       ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Validaciones
echo -e "${YELLOW}[1/5] Validando prerequisitos...${NC}"

# Verificar K6
if ! command -v k6 &> /dev/null; then
  echo -e "${RED}✗ K6 no encontrado. Instala desde https://grafana.com/docs/k6/latest/get-started/installation/${NC}"
  exit 1
fi
echo -e "${GREEN}✓ K6 instalado${NC}"

# Verificar backend
if ! curl -sf http://${API_HOST}/actuator/health > /dev/null 2>&1; then
  echo -e "${RED}✗ Backend no responde en http://${API_HOST}${NC}"
  echo -e "${YELLOW}  Inicia el backend: mvn quarkus:dev${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Backend OK en ${API_HOST}${NC}"

# Verificar fotos (múltiples formatos)
echo -e "${YELLOW}[2/5] Verificando fotos...${NC}"
PHOTOS=$(find ../../documents/pictures -type f \( -iname "acta-sample-*.jpg" -o -iname "acta-sample-*.jpeg" -o -iname "acta-sample-*.png" -o -iname "acta-sample-*.webp" \) 2>/dev/null | sort)
PHOTO_COUNT=$(echo "$PHOTOS" | grep -c . || true)

if [ "$PHOTO_COUNT" -eq 0 ]; then
  echo -e "${YELLOW}⚠ No se encontraron fotos en documents/pictures/${NC}"
  echo -e "${YELLOW}  Formatos soportados: .jpg, .jpeg, .png, .webp${NC}"
  echo -e "${YELLOW}  Script usará dummy JPEG si no existen${NC}"
  PHOTO_LIST=""
else
  echo -e "${GREEN}✓ $PHOTO_COUNT fotos encontradas:${NC}"
  echo "$PHOTOS" | sed 's/^/  - /' | head -5
  if [ "$PHOTO_COUNT" -gt 5 ]; then
    echo "  ... y $(($PHOTO_COUNT - 5)) más"
  fi
  # Convertir rutas absolutas a relativas para K6
  PHOTO_LIST=$(echo "$PHOTOS" | xargs -I {} basename {} | tr '\n' ',' | sed 's/,$//')
fi
echo ""

# Crear directorio de resultados
echo -e "${YELLOW}[3/5] Preparando directorios...${NC}"
mkdir -p "$RESULTS_DIR"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_FILE="$RESULTS_DIR/electoral-flow-${TIMESTAMP}.json"
echo -e "${GREEN}✓ Resultados en ${RESULTS_FILE}${NC}"

# Resumen de parámetros
echo ""
echo -e "${BLUE}Configuración:${NC}"
echo "  API Host:      ${API_HOST}"
echo "  VUs:           ${VUS}"
echo "  Duration:      ${DURATION}"
echo "  Resultados:    ${RESULTS_FILE}"
echo ""

# Estimación de timing
echo -e "${BLUE}Timing estimado:${NC}"
case "$DURATION" in
  2m)
    echo "  - Setup: ~30s"
    echo "  - OBSERVERs upload (280 actas): ~3-4 min"
    echo "  - COORDINATORs validate: ~1-2 min"
    echo "  - ADMIN dashboard: skipped"
    echo "  - Total: ~2 min"
    ;;
  5m)
    echo "  - Setup: ~30s"
    echo "  - OBSERVERs upload (280 actas): ~3-4 min"
    echo "  - COORDINATORs validate: ~1-2 min"
    echo "  - ADMIN dashboard: ~1 min"
    echo "  - Total: ~5-6 min"
    ;;
  10m)
    echo "  - Setup: ~30s"
    echo "  - OBSERVERs upload (280 actas): ~4-6 min"
    echo "  - COORDINATORs validate: ~2-3 min"
    echo "  - ADMIN dashboard: ~1 min"
    echo "  - Total: ~10-12 min"
    ;;
  *)
    echo "  - Custom duration: ${DURATION}"
    ;;
esac
echo ""

# Prompt confirmación
echo -e "${YELLOW}[4/5] Preparado para ejecutar. Presiona ENTER para comenzar...${NC}"
read -p ""

# Ejecutar K6
echo ""
echo -e "${YELLOW}[5/5] Iniciando load test...${NC}"
echo ""

k6 run scenarios/complete-electoral-flow.js \
  --vus "$VUS" \
  --duration "$DURATION" \
  --out json="$RESULTS_FILE" \
  -e API_HOST="${API_HOST}" \
  -e PHOTO_LIST="${PHOTO_LIST}" \
  2>&1 | tee "$RESULTS_DIR/electoral-flow-${TIMESTAMP}.log"

EXIT_CODE=$?

# Resultado
echo ""
echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
if [ $EXIT_CODE -eq 0 ]; then
  echo -e "${GREEN}✓ Test completado exitosamente${NC}"
  echo -e "${BLUE}║                                                               ║${NC}"
  echo -e "${BLUE}║ Próximo: Analizar resultados                                 ║${NC}"
  echo -e "${BLUE}║   jq '.metrics | .http_req_duration | .values' $RESULTS_FILE${NC}"
else
  echo -e "${RED}✗ Test fallido (salida: $EXIT_CODE)${NC}"
fi
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"

exit $EXIT_CODE
