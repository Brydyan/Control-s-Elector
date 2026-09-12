# =============================================================================
# Dockerfile — Frontend Angular + Ionic + Nginx Reverse Proxy
# =============================================================================
# Stage 1: Build
FROM node:22-alpine AS builder

WORKDIR /app

RUN npm install -g pnpm@9


# Dependencias con pnpm
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile || pnpm install

# Build de la aplicación
COPY . .
RUN pnpm build

# Stage 2: Serve con Nginx
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/www /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]