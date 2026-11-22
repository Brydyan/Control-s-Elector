# T-IN-09 — Deploy Angular en Cloudflare Pages

**Estado:** TODO
**Dificultad:** Intermedio
**Tiempo estimado:** 1 hora
**Depende de:** T-IN-07 ✅ (backend en Railway)

---

## ¿Qué vas a construir?

Deploy del frontend Angular en **Cloudflare Pages**: CDN global que sirve la app estática desde
edge locations cercanas a los usuarios, con SPA routing y **0 $ de egress**.

> La versión anterior de esta tarea describía el deploy en **Vercel**. Cloudflare Pages es el
> destino vigente del frontend. Se conserva el procedimiento (cuenta, import, build, dominio,
> verificación) con los valores reales.

---

## Pasos

### Paso 1 — Crear cuenta Cloudflare

1. Ve a https://dash.cloudflare.com
2. Regístrate (o entra con GitHub)
3. Autoriza acceso a repos

### Paso 2 — Crear proyecto Pages

1. Dashboard → **Workers & Pages** → **Create** → **Pages** → **Connect to Git**
2. Selecciona el repo `Control-s-Elector`
3. Build settings:
   - **Framework preset**: Angular
   - **Build command**: `cd frontend/app && npm run build`
   - **Build output directory**: `frontend/app/www`
   - **Root directory**: *(vacío — el repo es la raíz)*

> El `outputPath` real del proyecto es `frontend/app/www` (`angular.json` → `outputPath.base`).

### Paso 3 — Configuración en runtime

El frontend carga su configuración desde **`assets/config.json`** en runtime, no en build time
(ver `CLOUDFLARE_PAGES_SETUP.md`). El archivo ya versionado trae:

```json
{
  "apiUrl": "https://api.brydyan.me/api/v1",
  "sentryDsn": ""
}
```

Si se necesita cambiar la URL sin rebuild, se usa el post-build command de Cloudflare:

```bash
cat > frontend/app/www/assets/config.json << 'EOF'
{
  "apiUrl": "$CF_API_URL",
  "sentryDsn": "$CF_SENTRY_DSN"
}
EOF
```

### Paso 4 — SPA routing

Angular Router necesita que toda ruta interna sirva `index.html`. En Cloudflare Pages eso lo
resuelve el archivo `frontend/app/public/_redirects` (ver T-IN-10):

```
/* /index.html 200
```

### Paso 5 — Conectar dominio

1. Cloudflare Pages → **Custom domains**
2. Agrega: `control.brydyan.me`
3. Cloudflare actualiza el DNS si el dominio ya está en la misma cuenta

### Paso 6 — Verificar

```bash
curl -I https://control.brydyan.me
# Esperado: 200

# Una ruta interna también debe devolver 200 (no 404)
curl -I https://control.brydyan.me/observer/mis-actas
# Esperado: 200
```

---

## Checklist

- [ ] Cuenta Cloudflare creada
- [ ] Repo conectado a Pages
- [ ] Build config correcto (`frontend/app/www`)
- [ ] `public/_redirects` existe
- [ ] Dominio custom apuntando
- [ ] App carga sin 404 en rutas internas
- [ ] La API se alcanza por `https://api.brydyan.me/api/v1` (config.json)
