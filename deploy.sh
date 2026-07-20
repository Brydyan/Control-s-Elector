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

# Git pull
echo -e "${YELLOW}📥 Actualizando código desde develop...${NC}"
git pull origin develop || {
  echo -e "${RED}❌ Error al hacer git pull${NC}"
  exit 1
}

# Docker compose up
echo -e "${YELLOW}🐳 Iniciando contenedores...${NC}"
docker compose up -d || {
  echo -e "${RED}❌ Error al ejecutar docker compose${NC}"
  exit 1
}

echo -e "${GREEN}✅ Despliegue completado exitosamente${NC}"
echo -e "${GREEN}✨ Servidor staging actualizado en: /mnt/storage/projects/controls_elector/data${NC}"
