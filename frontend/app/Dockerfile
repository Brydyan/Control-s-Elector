# =============================================================================
# Dockerfile — Frontend Angular + Ionic
# =============================================================================
# Imagen unificada para desarrollo local. Para producción, el FE se deploya
# a Vercel desde la rama main (no se construye imagen de prod acá).
#
# ng serve con:
#   --host 0.0.0.0    escucha en todas las interfaces del container
#   --port 4200       puerto del dev server
#   --poll 2000       file watching por polling (inotify no funciona bien en containers)
#   --proxy-config    reenvía /api/* al backend electoral-api (ver proxy.conf.json)
# =============================================================================
FROM node:20-alpine

WORKDIR /app

# Dependencias primero (aprovecha la cache de Docker entre builds)
# Usamos `npm install` (no `npm ci`) porque el proyecto no tiene
# package-lock.json commiteado. Si lo generás (npm install local + commit),
# podés volver a `npm ci` para builds más determinísticos.
COPY package*.json ./
RUN npm install --no-audit --no-fund

# Código fuente (en dev se sobreescribe con bind mount para hot-reload)
COPY . .

EXPOSE 4200

CMD ["ng", "serve", "--host", "0.0.0.0", "--port", "4200", "--poll", "2000", "--proxy-config", "proxy.conf.json"]