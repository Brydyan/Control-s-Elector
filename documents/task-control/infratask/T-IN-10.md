# T-IN-10 — vercel.json para SPA

**Estado:** TODO
**Dificultad:** Principiante
**Tiempo estimado:** 15 min
**Depende de:** T-IN-09 (deploy Vercel)

---

## ¿Qué vas a construir?

Archivo `vercel.json`: configuración que redirige todas las rutas a `index.html` para que Angular Router funcione correctamente. Sin esto, refresh en `/observer/mis-actas` devuelve 404.

---

## Ubicación

```
frontend/app/
  vercel.json        ← Crear
```

---

## Contenido

```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

**Qué hace:**
- Cualquier ruta que no sea un archivo estático (`.js`, `.css`, `.png`, etc.) se redirige a `/index.html`
- Angular Router (en el navegador) maneja la ruta y renderiza el componente correcto

**Por qué es necesario:**
- Vercel sirve archivos estáticos. Sin redirección, `/observer/mis-actas` busca archivo `observer/mis-actas.html` (no existe)
- Con redirección, se sirve `index.html`, que carga Angular app
- Angular Router ve la URL en `location.pathname` y renderiza el componente

---

## Checklist

- [ ] Archivo creado en `frontend/app/vercel.json`
- [ ] Contenido copiado exactamente
- [ ] Comitear: `git add vercel.json && git commit -m "Add SPA routing"`
- [ ] Vercel redeploy automático
- [ ] Refresh en ruta Angular no devuelve 404
