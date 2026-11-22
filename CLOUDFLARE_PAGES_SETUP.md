# Cloudflare Pages — Configuración de Variables de Entorno

> Cloudflare Pages es el **destino de producción del frontend** (Angular estático) en `control.brydyan.me`.
> El backend Quarkus se despliega por separado en Railway (`api.brydyan.me`); la base de datos es
> Neon y las evidencias .webp viven en Cloudflare R2.

## Cómo funciona

El frontend carga configuración desde **`assets/config.json`** en **runtime** (no en build time).

Esto significa:
- ✅ **Sin rebuild** para cambiar URLs entre ambientes
- ✅ **Flexible**: Reemplaza solo el archivo `config.json` en deploy
- ✅ **Dev-friendly**: Las URLs relativas siguen funcionando con proxy

---

## Configuración en Cloudflare Pages

### 1. Upload personalizado de config.json

En el build output (después de que Cloudflare construya), reemplaza `config.json`:

**Option A: Build hook (recomendado)**

En **Settings → Builds & deployments → Build output settings** → Post-build commands:

```bash
# Reemplaza config.json después del build
cat > dist/app/assets/config.json << 'EOF'
{
  "apiUrl": "https://api.brydyan.me/api/v1",
  "sentryDsn": ""
}
EOF
```

Puedes inyectar variables de Cloudflare:
```bash
cat > dist/app/assets/config.json << 'EOF'
{
  "apiUrl": "$CF_API_URL",
  "sentryDsn": "$CF_SENTRY_DSN"
}
EOF
```

### 2. Build command (si no está automático)

En **Settings → Builds & deployments → Build settings**:

```
Build command:    npm run build
Build output:     frontend/app/dist/app
```

### 3. Environment variables en Cloudflare

En **Settings → Environment variables** (optional, para el post-build hook):

- `CF_API_URL` = `https://api.brydyan.me/api/v1`
- `CF_SENTRY_DSN` = (si lo usas)

---

## Uso local

### Dev (proxy relativo)
```bash
cd frontend/app
npm run build
ng serve
```
Automáticamente usa `/api/v1` del proxy local.

### Prod (URL absoluta)
```bash
cd frontend/app
npm run build
# Edita src/assets/config.json con tu URL
ng serve --configuration production --serve-path=/
```

---

## SPA redirect

Cloudflare Pages debe servir `index.html` para las rutas de Angular. En
`frontend/app/public/_redirects`:

```
/* /index.html 200
```

Sin esto, un refresh en una ruta interna devuelve 404.

---

## Qué cambió

- `src/assets/config.json`: Nueva. Contiene `apiUrl` y `sentryDsn`
- `src/app/core/config/config.service.ts`: Carga config.json en runtime
- `src/main.ts`: APP_INITIALIZER carga ConfigService antes de bootstrap
- `src/app/features/master/data/configuracion.api.ts`: Usa ConfigService
- **Fallback**: Si config.json falla, usa URL default
- **Sin rebuild**: Cambiar la URL es editar un archivo JSON, no recompilar

