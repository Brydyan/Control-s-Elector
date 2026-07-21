#!/bin/bash
set -e

# Colores para output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}🚀 Iniciando despliegue en servidor local...${NC}"

# Ruta del proyecto en HDD
PROJECT_PATH="/mnt/storage/projects/controls_elector/data"

# Verificar que la ruta existe
if [ ! -d "$PROJECT_PATH" ]; then
  echo -e "${RED}❌ Error: Ruta $PROJECT_PATH no existe${NC}"
  exit 1
fi

cd "$PROJECT_PATH"
echo -e "${YELLOW}📁 Directorio: $PROJECT_PATH${NC}"

# Git pull + capturar cambios
echo -e "${YELLOW}📥 Actualizando código desde develop...${NC}"
git fetch origin develop || {
  echo -e "${RED}❌ Error al hacer git fetch${NC}"
  exit 1
}
BEFORE=$(git rev-parse HEAD)
git pull origin develop || {
  echo -e "${RED}❌ Error al hacer git pull${NC}"
  exit 1
}
AFTER=$(git rev-parse HEAD)

# Detectar cambios en backend
echo -e "${YELLOW}🔍 Verificando cambios en backend...${NC}"
BACKEND_CHANGED=$(git diff --name-only $BEFORE $AFTER | grep -c "^backend/electoral-api/" || echo 0)

# Docker compose: rebuild backend solo si hay cambios
echo -e "${YELLOW}🐳 Iniciando contenedores...${NC}"
if [ "$BACKEND_CHANGED" -gt 0 ]; then
  echo -e "${YELLOW}♻️  Backend cambió — rebuilding imagen...${NC}"
  docker compose --profile dev build electoral-api || {
    echo -e "${RED}❌ Error al buildear electoral-api${NC}"
    exit 1
  }
else
  echo -e "${GREEN}✓ Backend sin cambios — usando imagen cached${NC}"
fi

docker compose --profile dev up -d || {
  echo -e "${RED}❌ Error al ejecutar docker compose${NC}"
  exit 1
}

echo -e "${GREEN}✅ Despliegue completado exitosamente${NC}"
echo -e "${GREEN}✨ Servidor staging actualizado en: /mnt/storage/projects/controls_elector/data${NC}"
