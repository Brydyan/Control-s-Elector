# T-IN-09 — Deploy Angular en Vercel

**Estado:** TODO
**Dificultad:** Intermedio
**Tiempo estimado:** 1 hora
**Depende de:** T-IN-07 ✅ (backend en Railway)

---

## ¿Qué vas a construir?

Deploy del frontend Angular en **Vercel**: plataforma global de CDN que sirve tu app desde edge locations cercanas a usuarios, con SPA routing automático.

---

## Pasos

### Paso 1 — Crear cuenta Vercel

1. Ve a https://vercel.com
2. Regístrate con GitHub
3. Autoriza acceso a repos

### Paso 2 — Importar proyecto

1. Dashboard → **Import Project**
2. Selecciona `Control-s-Elector` repo
3. Vercel detecta Angular automáticamente

### Paso 3 — Configurar build

1. **Framework Preset**: Angular
2. **Build Command**: `npm run build -- --configuration=production`
3. **Output Directory**: `dist/app/browser`
4. **Environment Variables**:
   ```
   VITE_API_URL=https://api.brydyan.me
   ```

### Paso 4 — Crear vercel.json

Archivo `frontend/app/vercel.json` (para SPA routing):

```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

Sin esto, refresh en ruta Angular devuelve 404.

### Paso 5 — Conectar dominio

1. Vercel Dashboard → **Settings** → **Domains**
2. Agrega: `control.brydyan.me`
3. Vercel retorna CNAME; actualiza tu DNS

### Paso 6 — Verificar

```bash
https://control.brydyan.me
# Debería cargar la app Angular
```

---

## Checklist

- [ ] Cuenta Vercel creada
- [ ] Repo importado
- [ ] Build config correcto (dist/app/browser)
- [ ] vercel.json existe
- [ ] Dominio custom apuntando
- [ ] App carga sin 404 en rutas
