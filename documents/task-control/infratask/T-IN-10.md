# T-IN-10 — `_redirects` para SPA en Cloudflare Pages

**Estado:** TODO
**Dificultad:** Principiante
**Tiempo estimado:** 15 min
**Depende de:** T-IN-09 (deploy en Cloudflare Pages)

---

## ¿Qué vas a construir?

Archivo `_redirects`: configuración que redirige todas las rutas a `index.html` para que Angular
Router funcione correctamente. Sin esto, refresh en `/observer/mis-actas` devuelve 404.

> La versión anterior de esta tarea usaba `vercel.json` con `rewrites`. Cloudflare Pages usa el
> archivo `_redirects`. Se conserva el objetivo, con el mecanismo del destino vigente.

---

## Ubicación

```
frontend/app/
  public/
    _redirects      ← Crear
```

El directorio `public/` de Angular se copia tal cual al build output, así que el archivo llega
a `frontend/app/www/_redirects`.

---

## Contenido

```
/* /index.html 200
```

**Qué hace:**
- Cualquier ruta que no sea un archivo estático (`.js`, `.css`, `.png`, etc.) se sirve desde
  `index.html` con status 200
- Angular Router (en el navegador) maneja la ruta y renderiza el componente correcto

**Por qué es necesario:**
- Pages sirve archivos estáticos. Sin redirección, `/observer/mis-actas` busca el archivo
  `observer/mis-actas.html` (no existe)
- Con redirección, se sirve `index.html`, que carga la app Angular
- Angular Router ve la URL en `location.pathname` y renderiza el componente

---

## Checklist

- [ ] Archivo creado en `frontend/app/public/_redirects`
- [ ] Contenido copiado exactamente
- [ ] Comitear: `git add frontend/app/public/_redirects && git commit -m "chore(frontend): SPA redirects"`
- [ ] Redeploy automático de Pages
- [ ] Refresh en ruta Angular no devuelve 404
