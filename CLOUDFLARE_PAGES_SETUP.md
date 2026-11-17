# Cloudflare Pages — Configuración de Variables de Entorno

## Requisito
El frontend Angular usa variable de entorno **`NG_APP_API_URL`** para configurar la URL de la API en producción.

## Configuración en Cloudflare Pages

### 1. Ingresar a Cloudflare Pages
1. Ir a tu proyecto en **Pages → Settings → Environment variables**

### 2. Agregar variable de producción
- **Variable name:** `NG_APP_API_URL`
- **Value:** `https://api.brydyan.me/api/v1` (o la URL que uses)
- **Environment:** Production (y/o Preview si quieres)

### 3. Build command (si no está automático)
En **Settings → Builds & deployments → Build settings**:

```
Build command:    npm run build
Build output:     frontend/app/dist/app
```

### 4. Deploy
En el siguiente deploy, Cloudflare inyectará `NG_APP_API_URL` automáticamente en el build.

---

## Uso local

Para testear localmente con la URL de producción:

```bash
cd frontend/app
NG_APP_API_URL=https://api.brydyan.me/api/v1 npm run build
ng serve --configuration production
```

O para dev (URL relativa):
```bash
npm run build
ng serve
```

---

## Variables disponibles

| Variable | Uso | Default |
|----------|-----|---------|
| `NG_APP_API_URL` | URL de API en producción | `https://api.brydyan.me/api/v1` |
| `NG_APP_SENTRY_DSN` | Sentry error tracking (futuro) | (vacío) |

---

## Cambios realizados

- `environment.prod.ts`: Ahora lee `NG_APP_API_URL` en tiempo de build
- Fallback: Si no se define, usa URL default
- No hay rebuild necesario entre ambientes: solo redeploy con variables diferentes

